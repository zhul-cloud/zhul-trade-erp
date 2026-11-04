import { act, fireEvent, render, screen } from '@testing-library/react';
import { App } from 'antd';
import React, { useState } from 'react';
import QuoteAttachments from '@/pages/inquiry/my-tasks/QuoteAttachments';
import { AttachmentWall } from './components';
import { attachmentApi } from './service';

/** audit UI-001：一次选多份附件时，每份上传成功都保留；上传期间删除的不会复活；不超过数量上限 */
jest.mock('./service', () => ({
  ...jest.requireActual('./service'),
  attachmentApi: { upload: jest.fn(), blobUrl: jest.fn() },
}));

/** 与 ./service 的 Attachment 同形；直接导入类型会和 jest.mock 冲突 */
interface Attachment {
  id: number;
  kind: number;
  fileName: string;
  fileSize: number;
  contentType: string;
}

const file = (id: number): Attachment => ({
  id,
  kind: 3,
  fileName: `f${id}.pdf`,
  fileSize: 1,
  contentType: 'application/pdf',
});

/** 每次上传返回一个由测试控制完成时机的 Promise */
const pending: { resolve: (a: Attachment) => void }[] = [];
(attachmentApi.upload as jest.Mock).mockImplementation(
  () =>
    new Promise<Attachment>((resolve) => {
      pending.push({ resolve });
    }),
);

let latest: Attachment[] = [];
const Host = ({
  initial,
  quote,
  max,
}: {
  initial: Attachment[];
  quote?: boolean;
  max?: number;
}) => {
  const [list, setList] = useState(initial);
  latest = list;
  return React.createElement(
    App,
    null,
    quote
      ? React.createElement(QuoteAttachments, {
          value: list,
          onChange: setList,
        })
      : React.createElement(AttachmentWall, {
          ownerType: 'EXPENSE',
          value: list,
          onChange: setList,
          max,
        }),
  );
};

const pick = async (count: number) => {
  const input = document.querySelector('input[type=file]') as HTMLInputElement;
  const files = Array.from(
    { length: count },
    (_, i) => new File(['x'], `n${i}.pdf`, { type: 'application/pdf' }),
  );
  await act(async () => {
    fireEvent.change(input, { target: { files } });
  });
};

const finish = async (index: number, a: Attachment) => {
  await act(async () => {
    pending[index].resolve(a);
  });
};

beforeEach(() => {
  pending.length = 0;
  latest = [];
  (attachmentApi.upload as jest.Mock).mockClear();
});

describe.each([
  ['单据附件墙', false],
  ['回价附件', true],
])('%s', (_label, quote) => {
  it('原有 1 份，一次选 3 份、按 3→1→2 完成：4 份都在', async () => {
    render(React.createElement(Host, { initial: [file(10)], quote: quote }));
    await pick(3);
    expect(pending).toHaveLength(3);
    await finish(2, file(13));
    await finish(0, file(11));
    await finish(1, file(12));
    expect(latest.map((a) => a.id).sort()).toEqual([10, 11, 12, 13]);
  });

  it('上传期间删掉原有附件，迟到的上传完成后原附件不会复活', async () => {
    render(React.createElement(Host, { initial: [file(10)], quote: quote }));
    await pick(1);
    fireEvent.click(screen.getByLabelText('删除 f10.pdf'));
    expect(latest.map((a) => a.id)).toEqual([]);
    await finish(0, file(11));
    expect(latest.map((a) => a.id)).toEqual([11]);
  });
});

it('一次选的份数超过剩余名额时，只上传剩余名额内的', async () => {
  render(React.createElement(Host, { initial: [file(10), file(11)], max: 3 }));
  await pick(3);
  expect(attachmentApi.upload).toHaveBeenCalledTimes(1);
});

it('回价附件每条最多 5 份：已有 3 份时一次选 4 份只上传 2 份', async () => {
  render(
    React.createElement(Host, {
      initial: [file(1), file(2), file(3)],
      quote: true,
    }),
  );
  await pick(4);
  expect(attachmentApi.upload).toHaveBeenCalledTimes(2);
});
