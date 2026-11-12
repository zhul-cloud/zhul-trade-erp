import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { App } from 'antd';
import React from 'react';
import { mediaApi } from '../../service';
import { MediaCard } from './MediaCard';

/** audit UI-050：商品档案的图片点开能预览，同一商品的图片可以左右切换 */
jest.mock('../../service', () => ({
  ...jest.requireActual('../../service'),
  mediaApi: { list: jest.fn() },
}));

const image = (id: number, title: string) => ({
  id,
  mediaType: 1,
  fileUrl: `https://example.com/${id}.png`,
  title,
  isMain: id === 1 ? 1 : 0,
  sortOrder: id,
  storageType: 1,
  fileSize: 1024,
});

it('点图片打开预览，可以切到下一张', async () => {
  (mediaApi.list as jest.Mock).mockResolvedValue([
    image(1, '正面'),
    image(2, '铭牌'),
  ]);
  render(
    React.createElement(
      App,
      null,
      React.createElement(MediaCard, {
        productId: 1,
        readOnly: true,
        onChanged: () => undefined,
      }),
    ),
  );
  fireEvent.click(await screen.findByAltText('正面'));
  await waitFor(() =>
    expect(document.querySelector('.ant-image-preview')).not.toBeNull(),
  );
  const current = () =>
    document.querySelector('.ant-image-preview img')?.getAttribute('src');
  expect(current()).toBe('https://example.com/1.png');
  fireEvent.keyDown(window, { key: 'ArrowRight', keyCode: 39 });
  await waitFor(() => expect(current()).toBe('https://example.com/2.png'));
});

/** audit UI-034：第一张设为主图后父级的 defaultSetMain 变化，不能把这一批的队列、标题、失败行清掉 */
it('多图上传中主图默认值变化，不重置当前批次', async () => {
  const service = jest.requireMock('../../service') as Record<string, unknown>;
  let rerenderModal: (setMain: boolean) => void = () => undefined;
  const upload = jest
    .fn()
    .mockImplementationOnce(async () => {
      // 第一张成功：父级刷新后有了主图，defaultSetMain 由 true 变 false
      rerenderModal(false);
    })
    .mockRejectedValueOnce(new Error('second failed'));
  service.uploadMedia = upload;
  const { UploadModal } = jest.requireActual(
    './MediaCard',
  ) as typeof import('./MediaCard');
  const view = (defaultSetMain: boolean) =>
    React.createElement(
      App,
      null,
      React.createElement(UploadModal, {
        productId: 1,
        open: true,
        defaultSetMain,
        onClose: () => undefined,
        onDone: () => undefined,
      }),
    );
  const { rerender } = render(view(true));
  rerenderModal = (v) => rerender(view(v));
  const input = document.querySelector(
    'input[type="file"]',
  ) as HTMLInputElement;
  const files = ['first.png', 'second.png'].map(
    (n) => new File(['x'], n, { type: 'image/png' }),
  );
  fireEvent.change(input, { target: { files } });
  await screen.findByText('second.png');
  fireEvent.change(
    document.getElementById('upload-title') as HTMLInputElement,
    {
      target: { value: 'KEEP TITLE' },
    },
  );
  fireEvent.click(screen.getByRole('button', { name: /上传（2）/ }));
  await waitFor(() => expect(upload).toHaveBeenCalledTimes(2));
  await waitFor(() =>
    expect(screen.getByRole('button', { name: /^上\s?传$/ })).toBeTruthy(),
  );
  expect(screen.getByText('second.png')).toBeTruthy();
  expect(
    (document.getElementById('upload-title') as HTMLInputElement).value,
  ).toBe('KEEP TITLE');
});
