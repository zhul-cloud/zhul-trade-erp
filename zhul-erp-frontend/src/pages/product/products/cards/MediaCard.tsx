import {
  ArrowDownOutlined,
  ArrowUpOutlined,
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

/** 上传弹窗（M09）：格式和大小先在浏览器里预检，通过后显示进度，可取消 */
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
  const [file, setFile] = useState<File>();
  const [title, setTitle] = useState('');
  const [setMain, setSetMain] = useState(!!defaultSetMain);
  const [progress, setProgress] = useState(0);
  const [uploading, setUploading] = useState(false);
  const [problem, setProblem] = useState<string>();
  const abort = useRef<AbortController>(undefined);

  useEffect(() => {
    if (open) {
      setMediaType(1);
      setFile(undefined);
      setTitle('');
      setSetMain(!!defaultSetMain);
      setProgress(0);
      setProblem(undefined);
    }
  }, [open, defaultSetMain]);

  const pick = (f: File, type: 1 | 2) => {
    setFile(f);
    setProblem(precheckUpload(f, type));
  };

  const submit = async () => {
    if (!file || problem) return;
    abort.current = new AbortController();
    setUploading(true);
    setProgress(0);
    try {
      await uploadMedia(productId, {
        file,
        mediaType,
        title: title.trim() || undefined,
        setMain: mediaType === 1 && setMain,
        onProgress: setProgress,
        signal: abort.current.signal,
      });
      message.success('已上传');
      onDone();
      onClose();
    } catch (e) {
      if ((e as { code?: string })?.code === 'ERR_CANCELED') {
        message.info('已取消上传');
      } else {
        // 服务端再次校验失败时给出具体原因，可以重试
        setProblem(readBizError(e).message);
      }
    } finally {
      setUploading(false);
    }
  };

  const close = () => {
    abort.current?.abort();
    onClose();
  };

  return (
    <Modal
      open={open}
      title="上传图片或视频"
      onCancel={close}
      destroyOnHidden
      footer={[
        <Button key="cancel" onClick={close}>
          {uploading ? '取消上传' : '取消'}
        </Button>,
        <Button
          key="ok"
          type="primary"
          disabled={!file || !!problem || uploading}
          loading={uploading}
          onClick={submit}
        >
          上传
        </Button>,
      ]}
    >
      <Space orientation="vertical" size={16} style={{ width: '100%' }}>
        <Radio.Group
          value={mediaType}
          onChange={(e) => {
            setMediaType(e.target.value);
            setFile(undefined);
            setProblem(undefined);
          }}
          optionType="button"
          options={[
            { value: 1, label: '图片' },
            { value: 2, label: '视频' },
          ]}
        />
        <Upload.Dragger
          accept={mediaType === 1 ? '.jpg,.jpeg,.png,.webp' : '.mp4,.webm'}
          multiple={false}
          showUploadList={false}
          disabled={uploading}
          beforeUpload={(f) => {
            pick(f, mediaType);
            return false;
          }}
        >
          <p style={{ fontSize: 16, margin: '8px 0' }}>
            <UploadOutlined /> 把文件拖到这里，或点击选择
          </p>
          <p style={{ color: palette.sub, fontSize: 13, margin: 0 }}>
            {mediaType === 1
              ? '支持 jpg、jpeg、png、webp，不超过 5MB'
              : '支持 mp4、webm，不超过 100MB'}
          </p>
        </Upload.Dragger>
        {file && (
          <div>
            <div style={{ fontSize: 14 }}>
              {file.name} · <span className="num">{formatSize(file.size)}</span>
            </div>
            {problem && (
              <div
                role="alert"
                style={{ color: palette.red, fontSize: 13, marginTop: 4 }}
              >
                {problem}
              </div>
            )}
          </div>
        )}
        {uploading && <Progress percent={progress} aria-label="上传进度" />}
        <div>
          <label
            htmlFor="upload-title"
            style={{ display: 'block', marginBottom: 4, fontSize: 13 }}
          >
            标题{mediaType === 1 ? '（同时作为图片的替代文字）' : ''}
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
            设为主图
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
