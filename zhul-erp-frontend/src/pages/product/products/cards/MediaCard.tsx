import {
  ArrowDownOutlined,
  ArrowUpOutlined,
  CheckCircleFilled,
  CloseCircleFilled,
  DeleteOutlined,
  PlayCircleFilled,
  StarFilled,
  UploadOutlined,
} from '@ant-design/icons';
import {
  App,
  Button,
  Checkbox,
  Form,
  Input,
  Modal,
  Progress,
  Radio,
  Space,
  Upload,
} from 'antd';
import React, { useCallback, useEffect, useRef, useState } from 'react';
import { EmptyHint } from '../../components/EmptyHint';
import { Pill } from '../../components/Pills';
import { isSafeUrl, precheckUpload, URL_RULE_MESSAGE } from '../../constants';
import {
  type MediaItem,
  mediaApi,
  readBizError,
  uploadMedia,
} from '../../service';
import { useProductTheme } from '../../theme';
import { CardEmpty, CardShell } from './CardShell';

const formatSize = (n: number) =>
  n >= 1024 * 1024
    ? `${(n / 1024 / 1024).toFixed(1)} MB`
    : `${Math.max(1, Math.round(n / 1024))} KB`;

interface PendingFile {
  uid: string;
  file: File;
  previewUrl?: string;
  problem?: string;
  status: 'pending' | 'uploading' | 'done' | 'error';
  progress: number;
  error?: string;
}

let pendingUidSeq = 0;
const nextPendingUid = () => `f${Date.now()}-${pendingUidSeq++}`;

/**
 * 上传弹窗（M09）：格式和大小先在浏览器里预检，通过后显示进度，可取消。
 * 图片支持一次选择多张，按顺序逐个上传（复用单文件上传接口），互不阻塞——
 * 某一张失败不影响其它张继续；重新点「上传」会自动重试失败/未完成的部分。
 * 视频仍保持单文件（本来体积就大，一次一个更稳）。
 */
export const UploadModal: React.FC<{
  productId: number;
  open: boolean;
  defaultSetMain?: boolean;
  onClose: () => void;
  onDone: () => void;
}> = ({ productId, open, defaultSetMain, onClose, onDone }) => {
  const { message } = App.useApp();
  const { palette } = useProductTheme();
  const [mediaType, setMediaType] = useState<1 | 2>(1);
  const [files, setFiles] = useState<PendingFile[]>([]);
  const [title, setTitle] = useState('');
  const [setMain, setSetMain] = useState(!!defaultSetMain);
  const [uploading, setUploading] = useState(false);
  const abort = useRef<AbortController>(undefined);
  const cancelled = useRef(false);
  const createdUrls = useRef<string[]>([]);

  // 弹窗销毁（destroyOnHidden）时统一回收所有生成过的缩略图 objectURL，避免内存泄漏
  useEffect(() => {
    return () => {
      createdUrls.current.forEach((u) => {
        URL.revokeObjectURL(u);
      });
    };
  }, []);

  useEffect(() => {
    if (open) {
      setMediaType(1);
      setFiles([]);
      setTitle('');
      setSetMain(!!defaultSetMain);
      setUploading(false);
    }
  }, [open, defaultSetMain]);

  const addFiles = (list: File[], type: 1 | 2) => {
    const items: PendingFile[] = list.map((f) => {
      const previewUrl = type === 1 ? URL.createObjectURL(f) : undefined;
      if (previewUrl) createdUrls.current.push(previewUrl);
      return {
        uid: nextPendingUid(),
        file: f,
        previewUrl,
        problem: precheckUpload(f, type),
        status: 'pending',
        progress: 0,
      };
    });
    // 视频保持单文件：重新选择即替换；图片允许多次选择累加
    setFiles((prev) => (type === 2 ? items.slice(0, 1) : [...prev, ...items]));
  };

  const removeFile = (uid: string) => {
    setFiles((prev) => {
      const target = prev.find((f) => f.uid === uid);
      if (target?.previewUrl) URL.revokeObjectURL(target.previewUrl);
      return prev.filter((f) => f.uid !== uid);
    });
  };

  const submit = async () => {
    const targets = files.filter((f) => f.status !== 'done');
    if (targets.length === 0 || uploading) return;
    setUploading(true);
    cancelled.current = false;
    let successCount = 0;
    let mainAssigned = false;
    for (const f of targets) {
      if (cancelled.current) break;
      abort.current = new AbortController();
      setFiles((prev) =>
        prev.map((x) =>
          x.uid === f.uid
            ? { ...x, status: 'uploading', progress: 0, error: undefined }
            : x,
        ),
      );
      try {
        await uploadMedia(productId, {
          file: f.file,
          mediaType,
          title: title.trim() || undefined,
          setMain: mediaType === 1 && setMain && !mainAssigned,
          onProgress: (p) =>
            setFiles((prev) =>
              prev.map((x) => (x.uid === f.uid ? { ...x, progress: p } : x)),
            ),
          signal: abort.current.signal,
        });
        mainAssigned = mainAssigned || (mediaType === 1 && setMain);
        successCount += 1;
        setFiles((prev) =>
          prev.map((x) =>
            x.uid === f.uid ? { ...x, status: 'done', progress: 100 } : x,
          ),
        );
        onDone(); // 每张成功即刷新底部列表，取消剩余部分时已上传的不会丢
      } catch (e) {
        if ((e as { code?: string })?.code === 'ERR_CANCELED') {
          setFiles((prev) =>
            prev.map((x) =>
              x.uid === f.uid ? { ...x, status: 'pending', progress: 0 } : x,
            ),
          );
          break;
        }
        // 服务端再次校验失败时给出具体原因，重新点「上传」会重试
        setFiles((prev) =>
          prev.map((x) =>
            x.uid === f.uid
              ? {
                  ...x,
                  status: 'error',
                  progress: 0,
                  error: readBizError(e).message,
                }
              : x,
          ),
        );
      }
    }
    setUploading(false);
    if (successCount === 0) return;
    if (successCount === targets.length) {
      message.success(
        targets.length === 1 ? '已上传' : `已上传 ${targets.length} 张`,
      );
      onClose();
    } else {
      message.info(
        `已上传 ${successCount}/${targets.length}，其余失败，可重试或移除后再试`,
      );
    }
  };

  const cancelUpload = () => {
    if (uploading) {
      cancelled.current = true;
      abort.current?.abort();
    } else {
      onClose();
    }
  };

  const close = () => {
    cancelled.current = true;
    abort.current?.abort();
    onClose();
  };

  const hasBlockingProblem = files.some((f) => f.problem);
  const pendingCount = files.filter((f) => f.status !== 'done').length;

  return (
    <Modal
      open={open}
      title="上传图片或视频"
      onCancel={close}
      destroyOnHidden
      footer={[
        <Button key="cancel" onClick={cancelUpload}>
          {uploading ? '取消上传' : '取消'}
        </Button>,
        <Button
          key="ok"
          type="primary"
          disabled={pendingCount === 0 || hasBlockingProblem || uploading}
          loading={uploading}
          onClick={submit}
        >
          {pendingCount > 1 ? `上传（${pendingCount}）` : '上传'}
        </Button>,
      ]}
    >
      <Space orientation="vertical" size={16} style={{ width: '100%' }}>
        <Radio.Group
          value={mediaType}
          onChange={(e) => {
            setMediaType(e.target.value);
            files.forEach((f) => {
              if (f.previewUrl) URL.revokeObjectURL(f.previewUrl);
            });
            setFiles([]);
          }}
          optionType="button"
          options={[
            { value: 1, label: '图片' },
            { value: 2, label: '视频' },
          ]}
        />
        <Upload.Dragger
          accept={mediaType === 1 ? '.jpg,.jpeg,.png,.webp' : '.mp4,.webm'}
          multiple={mediaType === 1}
          showUploadList={false}
          disabled={uploading}
          beforeUpload={(f, fileList) => {
            // antd 对多选的每个文件都会调用一次 beforeUpload；fileList 是本次选择的全部文件，
            // 只在处理第一个时整批加入，避免同一批被重复 addFiles 多次
            if (fileList[0] === f) addFiles(fileList, mediaType);
            return false;
          }}
        >
          <p style={{ fontSize: 16, margin: '8px 0' }}>
            <UploadOutlined /> 把文件拖到这里，或点击选择
          </p>
          <p style={{ color: palette.sub, fontSize: 13, margin: 0 }}>
            {mediaType === 1
              ? '支持 jpg、jpeg、png、webp，不超过 5MB，可一次选择多张'
              : '支持 mp4、webm，不超过 100MB'}
          </p>
        </Upload.Dragger>
        {files.length > 0 && (
          <div
            style={{
              maxHeight: 260,
              overflowY: 'auto',
              display: 'flex',
              flexDirection: 'column',
              gap: 8,
            }}
          >
            {files.map((f) => (
              <div
                key={f.uid}
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  gap: 8,
                  padding: '6px 8px',
                  border: `1px solid ${palette.hairline}`,
                  borderRadius: 8,
                }}
              >
                {f.previewUrl && (
                  <div
                    style={{
                      width: 40,
                      height: 40,
                      borderRadius: 6,
                      overflow: 'hidden',
                      flexShrink: 0,
                      background: palette.inset,
                    }}
                  >
                    <img
                      src={f.previewUrl}
                      alt=""
                      style={{
                        width: '100%',
                        height: '100%',
                        objectFit: 'cover',
                      }}
                    />
                  </div>
                )}
                <div style={{ flex: 1, minWidth: 0 }}>
                  <div
                    style={{
                      fontSize: 13,
                      overflow: 'hidden',
                      textOverflow: 'ellipsis',
                      whiteSpace: 'nowrap',
                    }}
                    title={f.file.name}
                  >
                    {f.file.name}
                  </div>
                  <div style={{ fontSize: 12, color: palette.sub }}>
                    <span className="num">{formatSize(f.file.size)}</span>
                    {f.problem && (
                      <span
                        role="alert"
                        style={{ color: palette.red, marginLeft: 8 }}
                      >
                        {f.problem}
                      </span>
                    )}
                    {f.status === 'error' && (
                      <span
                        role="alert"
                        style={{ color: palette.red, marginLeft: 8 }}
                      >
                        {f.error}
                      </span>
                    )}
                  </div>
                  {f.status === 'uploading' && (
                    <Progress
                      percent={f.progress}
                      size="small"
                      showInfo={false}
                      aria-label={`${f.file.name} 上传进度`}
                    />
                  )}
                </div>
                <div style={{ flexShrink: 0 }}>
                  {f.status === 'done' ? (
                    <CheckCircleFilled
                      style={{ color: palette.green, fontSize: 16 }}
                    />
                  ) : f.status === 'uploading' ? (
                    <span
                      className="num"
                      style={{ fontSize: 12, color: palette.sub }}
                    >
                      {f.progress}%
                    </span>
                  ) : f.status === 'error' ? (
                    <Space size={4}>
                      <CloseCircleFilled
                        style={{ color: palette.red, fontSize: 16 }}
                      />
                      <Button
                        size="small"
                        type="text"
                        aria-label={`移除 ${f.file.name}`}
                        icon={<DeleteOutlined />}
                        onClick={() => removeFile(f.uid)}
                      />
                    </Space>
                  ) : (
                    <Button
                      size="small"
                      type="text"
                      aria-label={`移除 ${f.file.name}`}
                      icon={<DeleteOutlined />}
                      disabled={uploading}
                      onClick={() => removeFile(f.uid)}
                    />
                  )}
                </div>
              </div>
            ))}
          </div>
        )}
        <div>
          <label
            htmlFor="upload-title"
            style={{ display: 'block', marginBottom: 4, fontSize: 13 }}
          >
            标题
            {mediaType === 1
              ? `（同时作为图片的替代文字${files.length > 1 ? '，应用到本次所有图片' : ''}）`
              : ''}
          </label>
          <Input
            id="upload-title"
            value={title}
            maxLength={128}
            onChange={(e) => setTitle(e.target.value)}
          />
        </div>
        {mediaType === 1 && (
          <Checkbox
            checked={setMain}
            onChange={(e) => setSetMain(e.target.checked)}
          >
            设为主图{files.length > 1 ? '（取本次第一张上传成功的）' : ''}
          </Checkbox>
        )}
      </Space>
    </Modal>
  );
};

export const LinkModal: React.FC<{
  productId: number;
  open: boolean;
  onClose: () => void;
  onDone: () => void;
}> = ({ productId, open, onClose, onDone }) => {
  const { message } = App.useApp();
  const [form] = Form.useForm();
  const type = Form.useWatch('mediaType', form);
  return (
    <Modal
      open={open}
      title="添加外部链接"
      onCancel={onClose}
      destroyOnHidden
      okText="添加"
      cancelText="取消"
      onOk={async () => {
        const v = await form.validateFields();
        await mediaApi.register(productId, {
          ...v,
          setMain: v.mediaType === 1 && v.setMain,
        });
        message.success('已添加');
        onDone();
        onClose();
      }}
    >
      <Form
        form={form}
        layout="vertical"
        initialValues={{ mediaType: 1, setMain: false }}
        preserve={false}
      >
        <Form.Item name="mediaType" label="类型">
          <Radio.Group
            optionType="button"
            options={[
              { value: 1, label: '图片' },
              { value: 2, label: '视频' },
            ]}
          />
        </Form.Item>
        <Form.Item
          name="fileUrl"
          label="地址"
          validateTrigger="onBlur"
          rules={[
            { required: true, message: '请输入地址' },
            {
              validator: (_, v) =>
                !v || isSafeUrl(v)
                  ? Promise.resolve()
                  : Promise.reject(new Error(URL_RULE_MESSAGE)),
            },
          ]}
        >
          <Input placeholder="https://" />
        </Form.Item>
        {type === 2 && (
          <Form.Item
            name="coverUrl"
            label="封面地址（可选）"
            validateTrigger="onBlur"
            rules={[
              {
                validator: (_, v) =>
                  !v || isSafeUrl(v)
                    ? Promise.resolve()
                    : Promise.reject(new Error(URL_RULE_MESSAGE)),
              },
            ]}
          >
            <Input placeholder="https://" />
          </Form.Item>
        )}
        <Form.Item name="title" label="标题">
          <Input maxLength={128} />
        </Form.Item>
        {type !== 2 && (
          <Form.Item name="setMain" valuePropName="checked">
            <Checkbox>设为主图</Checkbox>
          </Form.Item>
        )}
      </Form>
    </Modal>
  );
};

export const MediaCard: React.FC<{
  productId: number;
  readOnly: boolean;
  done?: boolean;
  onChanged: () => void;
}> = ({ productId, readOnly, done, onChanged }) => {
  const { message, modal } = App.useApp();
  const { palette } = useProductTheme();
  const [items, setItems] = useState<MediaItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string>();
  const [uploadOpen, setUploadOpen] = useState(false);
  const [linkOpen, setLinkOpen] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    setError(undefined);
    try {
      setItems(await mediaApi.list(productId));
    } catch (e) {
      setError(readBizError(e).message);
    } finally {
      setLoading(false);
    }
  }, [productId]);

  useEffect(() => {
    load();
  }, [load]);

  const changed = () => {
    load();
    onChanged();
  };

  const setMain = async (m: MediaItem) => {
    await mediaApi.setMain(productId, m.id);
    message.success('已设为主图');
    changed();
  };

  const remove = (m: MediaItem) =>
    modal.confirm({
      title: '删除这个文件？',
      content: '文件记录会被移除，之后可以重新上传。',
      okText: '删除',
      okButtonProps: { danger: true },
      cancelText: '取消',
      onOk: async () => {
        await mediaApi.remove(productId, m.id);
        message.success('已删除');
        changed();
      },
    });

  // 排序：除了拖动，键盘和单指也能用「上移 / 下移」
  const move = async (index: number, delta: -1 | 1) => {
    const target = index + delta;
    if (target < 0 || target >= items.length) return;
    const next = [...items];
    [next[index], next[target]] = [next[target], next[index]];
    await Promise.all(
      next.map((m, i) =>
        m.sortOrder === i
          ? undefined
          : mediaApi.update(productId, m.id, { sortOrder: i }),
      ),
    );
    load();
  };

  const hasMain = items.some((m) => m.isMain === 1);

  return (
    <CardShell
      id="card-media"
      title="图片与视频"
      done={done}
      actions={
        !readOnly && (
          <Space>
            <Button onClick={() => setLinkOpen(true)}>添加链接</Button>
            <Button
              icon={<UploadOutlined />}
              onClick={() => setUploadOpen(true)}
            >
              上传
            </Button>
          </Space>
        )
      }
    >
      {error ? (
        <EmptyHint
          title="加载失败"
          description={error}
          actionText="重试"
          onAction={load}
        />
      ) : loading ? (
        <div style={{ color: palette.sub }}>正在加载…</div>
      ) : items.length === 0 ? (
        <CardEmpty
          benefit="有主图，客户才能一眼认出这个商品；图片支持 jpg、png、webp，视频支持 mp4、webm。"
          actionText={readOnly ? undefined : '上传第一张图片'}
          onAction={() => setUploadOpen(true)}
        />
      ) : (
        <>
          {!hasMain && !readOnly && (
            <div
              role="status"
              style={{ marginBottom: 12, color: palette.orange, fontSize: 13 }}
            >
              还没有设置主图：点图片下方的「设为主图」，列表和询盘里就会显示它。
            </div>
          )}
          <ul
            style={{
              listStyle: 'none',
              margin: 0,
              padding: 0,
              display: 'grid',
              gap: 16,
              gridTemplateColumns: 'repeat(auto-fill, minmax(180px, 1fr))',
            }}
          >
            {items.map((m, i) => (
              <li
                key={m.id}
                style={{
                  border: `1px solid ${m.isMain ? palette.link : palette.hairline}`,
                  borderRadius: 12,
                  overflow: 'hidden',
                  background: palette.inset,
                }}
              >
                <div
                  style={{
                    position: 'relative',
                    aspectRatio: '4 / 3',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    background: m.mediaType === 2 ? '#0B1220' : palette.inset,
                  }}
                >
                  {m.mediaType === 1 ? (
                    <img
                      src={m.fileUrl}
                      alt={m.title || '商品图片'}
                      style={{
                        width: '100%',
                        height: '100%',
                        objectFit: 'contain',
                      }}
                    />
                  ) : (
                    <a
                      href={m.fileUrl}
                      target="_blank"
                      rel="noreferrer"
                      aria-label={`播放视频 ${m.title || ''}`}
                      style={{ color: '#F8FAFC', fontSize: 40 }}
                    >
                      <PlayCircleFilled />
                    </a>
                  )}
                  {m.isMain === 1 && (
                    <span style={{ position: 'absolute', top: 8, left: 8 }}>
                      <Pill tone="accent">
                        <StarFilled style={{ marginRight: 4 }} />
                        主图
                      </Pill>
                    </span>
                  )}
                </div>
                <div style={{ padding: 12 }}>
                  <div
                    style={{
                      fontSize: 13,
                      overflow: 'hidden',
                      textOverflow: 'ellipsis',
                      whiteSpace: 'nowrap',
                    }}
                    title={m.title}
                  >
                    {m.title ||
                      (m.mediaType === 1 ? '未命名图片' : '未命名视频')}
                  </div>
                  <div style={{ fontSize: 12, color: palette.mute }}>
                    {m.storageType === 1
                      ? `平台上传 · ${formatSize(m.fileSize)}`
                      : '外部链接'}
                  </div>
                  {!readOnly && (
                    <Space size={4} wrap style={{ marginTop: 8 }}>
                      {m.mediaType === 1 && m.isMain !== 1 && (
                        <Button
                          size="small"
                          type="link"
                          onClick={() => setMain(m)}
                        >
                          设为主图
                        </Button>
                      )}
                      <Button
                        size="small"
                        aria-label="上移"
                        icon={<ArrowUpOutlined />}
                        disabled={i === 0}
                        onClick={() => move(i, -1)}
                      />
                      <Button
                        size="small"
                        aria-label="下移"
                        icon={<ArrowDownOutlined />}
                        disabled={i === items.length - 1}
                        onClick={() => move(i, 1)}
                      />
                      <Button
                        size="small"
                        aria-label="删除"
                        danger
                        icon={<DeleteOutlined />}
                        onClick={() => remove(m)}
                      />
                    </Space>
                  )}
                </div>
              </li>
            ))}
          </ul>
        </>
      )}
      <UploadModal
        productId={productId}
        open={uploadOpen}
        defaultSetMain={!hasMain}
        onClose={() => setUploadOpen(false)}
        onDone={changed}
      />
      <LinkModal
        productId={productId}
        open={linkOpen}
        onClose={() => setLinkOpen(false)}
        onDone={changed}
      />
    </CardShell>
  );
};
