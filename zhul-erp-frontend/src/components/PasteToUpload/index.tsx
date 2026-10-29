import { App } from 'antd';
import dayjs from 'dayjs';
import { useEffect } from 'react';

/** antd Upload 的外层，里面是隐藏的 input[type=file] */
const UPLOAD = '.ant-upload';
/** 打开着的弹窗、抽屉：有上传区域时只粘贴到它们里面，不粘到被盖住的页面 */
const LAYER = '.ant-modal, .ant-drawer-content';

const visible = (el: Element) => el.getClientRects().length > 0;

const inputs = (): HTMLInputElement[] =>
  Array.from(
    document.querySelectorAll<HTMLInputElement>(`${UPLOAD} input[type=file]`),
  ).filter((i) => {
    const wrap = i.closest(UPLOAD);
    return !i.disabled && wrap != null && visible(wrap);
  });

/** 与 input 的 accept 比对，如 "image/*,.pdf" */
const accepts = (input: HTMLInputElement, file: File) => {
  const rules = input.accept
    .split(',')
    .map((r) => r.trim().toLowerCase())
    .filter(Boolean);
  if (rules.length === 0) return true;
  const name = file.name.toLowerCase();
  const type = file.type.toLowerCase();
  return rules.some((r) =>
    r.startsWith('.')
      ? name.endsWith(r)
      : r.endsWith('/*')
        ? type.startsWith(r.slice(0, -1))
        : type === r,
  );
};

/** 截图粘贴时文件名都是 image.png，加上时间避免重名 */
const rename = (file: File, i: number) => {
  if (!/^image\.\w+$/i.test(file.name)) return file;
  const ext = file.name.slice(file.name.lastIndexOf('.'));
  const suffix = i > 0 ? `-${i + 1}` : '';
  return new File(
    [file],
    `粘贴图片-${dayjs().format('YYYYMMDD-HHmmss')}${suffix}${ext}`,
    { type: file.type },
  );
};

/**
 * 全局粘贴上传：剪贴板里有图片或文件时（如截图、在文件夹里复制的文件），
 * 交给最近点过或鼠标停留过的上传区域；没有时，页面（或打开的弹窗、抽屉）里只有一个上传区域就交给它。
 * 不改各处 Upload 的写法：把文件放进它的 input[type=file] 并触发 change，走组件原来的校验与上传。
 * 剪贴板里只有文字时不处理，照常粘贴。
 */
const PasteToUpload: React.FC = () => {
  const { message } = App.useApp();

  useEffect(() => {
    let last: HTMLInputElement | null = null;
    const remember = (e: Event) => {
      const wrap = (e.target as Element | null)?.closest?.(UPLOAD);
      const input = wrap?.querySelector<HTMLInputElement>('input[type=file]');
      if (input) last = input;
    };

    const onPaste = (e: ClipboardEvent) => {
      const pasted = Array.from(e.clipboardData?.files ?? []);
      if (pasted.length === 0) return;
      let candidates = inputs();
      const layered = candidates.filter((i) => i.closest(LAYER));
      if (layered.length > 0) candidates = layered;
      if (candidates.length === 0) return;
      const target =
        last && candidates.includes(last)
          ? last
          : candidates.length === 1
            ? candidates[0]
            : undefined;
      e.preventDefault();
      if (!target) {
        message.info('这里有多个上传区域，请先点一下要放附件的那个，再粘贴');
        return;
      }
      const files = pasted
        .filter((f) => accepts(target, f))
        .slice(0, target.multiple ? undefined : 1)
        .map(rename);
      if (files.length === 0) {
        message.warning('这里不支持上传这种文件');
        return;
      }
      const dt = new DataTransfer();
      for (const f of files) dt.items.add(f);
      target.files = dt.files;
      target.dispatchEvent(new Event('change', { bubbles: true }));
    };

    document.addEventListener('pointerover', remember, true);
    document.addEventListener('focusin', remember, true);
    document.addEventListener('paste', onPaste);
    return () => {
      document.removeEventListener('pointerover', remember, true);
      document.removeEventListener('focusin', remember, true);
      document.removeEventListener('paste', onPaste);
    };
  }, [message]);

  return null;
};

export default PasteToUpload;
