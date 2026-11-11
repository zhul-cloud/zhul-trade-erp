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
