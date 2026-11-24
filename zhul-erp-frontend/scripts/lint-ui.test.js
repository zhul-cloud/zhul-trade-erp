const path = require('node:path');
const { checkSource } = require('./lint-ui');

const PAGE = path.join('src', 'pages', 'sales', 'demo.tsx');
const COMP = path.join('src', 'components', 'Demo', 'index.tsx');
const rules = (src, rel = PAGE) =>
  checkSource(src, rel).map((i) => `${i.rule}:${i.level}`);

describe('lint:ui', () => {
  it('查出渐变与发光', () => {
    expect(
      rules("const a = { background: 'linear-gradient(90deg, red, blue)' };"),
    ).toContain('gradient:error');
    expect(rules('ctx.createLinearGradient(0, 0, 1, 1);', COMP)).toContain(
      'gradient:error',
    );
    expect(rules("const a = { textShadow: '0 0 4px red' };", COMP)).toContain(
      'glow:error',
    );
    expect(
      rules(
        "const a = { boxShadow: '0 0 24px rgba(59, 130, 246, 0.6)' };",
        COMP,
      ),
    ).toContain('glow:error');
    // 普通投影、焦点环不算发光
    expect(
      rules("const a = { boxShadow: '0 0 0 4px #637381' };", COMP),
    ).not.toContain('glow:error');
    expect(
      rules(
        "const a = { boxShadow: '0 8px 16px rgba(145,158,171,.24)' };",
        COMP,
      ),
    ).not.toContain('glow:error');
  });

  it('页面里写死的颜色报错，纯白和组件目录不报', () => {
    expect(rules("const a = { color: '#2563EB' };")).toContain(
      'hardcoded-color:error',
    );
    expect(rules("const a = { color: 'rgba(0, 0, 0, .5)' };")).toContain(
      'hardcoded-color:error',
    );
    expect(rules("const a = { color: '#fff' };")).not.toContain(
      'hardcoded-color:error',
    );
    expect(rules("const a = { color: '#FFFFFF' };")).not.toContain(
      'hardcoded-color:error',
    );
    expect(rules("const a = { color: '#2563EB' };", COMP)).not.toContain(
      'hardcoded-color:error',
    );
    // 链接锚点不是颜色
    expect(rules('<a href="#main">跳</a>')).not.toContain(
      'hardcoded-color:error',
    );
  });

  it('旧页头、面包屑与旧等宽字体', () => {
    expect(rules('<PageTitle title="客户" description="说明" />')).toContain(
      'old-header:error',
    );
    expect(rules('<Breadcrumb items={items} />')).toContain('old-header:error');
    expect(rules("<Detail crumbs={['客户']} />")).toContain('old-header:error');
    expect(rules("const a = { fontFamily: 'IBM Plex Mono' };")).toContain(
      'ibm-plex:error',
    );
  });

  it('常识性套话报错，「保存后生效」只提示', () => {
    expect(rules('<span>点击进入对应列表</span>')).toContain('filler:error');
    expect(rules('<span>保存后生效</span>')).toContain('filler-maybe:warn');
  });

  it('确认弹窗、警告条、气泡确认里的文字不检查', () => {
    const src = [
      'modal.confirm({',
      "  title: '确认',",
      "  content: '点击进入对应列表',",
      '});',
      '<Alert type="warning" message="保存后生效" />',
      '<Popconfirm title="点击可跳转">',
      '  <a>删</a>',
      '</Popconfirm>',
    ].join('\n');
    expect(rules(src)).toEqual([]);
  });

  it('搜索框提示超过三项报错；其余超过三项或过长只提示；多行文本框、网址不按项拆', () => {
    expect(
      rules(
        '<Input prefix={<SearchOutlined />} placeholder="订单编号、PI 编号、客户、型号" />',
      ),
    ).toContain('long-placeholder:error');
    expect(
      rules('<Input.Search placeholder="订单编号、PI 编号、客户、型号" />'),
    ).toContain('long-placeholder:error');
    expect(
      rules(
        '<FilterField label="关键词"><Input placeholder="订单编号、PI 编号、客户、型号" /></FilterField>',
      ),
    ).toContain('long-placeholder:error');
    expect(
      rules('<Input placeholder="大写字母、数字、下划线、横线" />'),
    ).toEqual(['long-placeholder:warn']);
    expect(rules('<Input placeholder="订单编号、客户、型号" />')).toEqual([]);
    expect(
      rules(
        '<Input placeholder="请输入客户的完整英文名称或者常用的英文简称" />',
      ),
    ).toContain('long-placeholder:warn');
    expect(
      rules(
        '<Input.TextArea placeholder="订单编号、PI 编号、客户、型号、备注" />',
      ),
    ).toEqual([]);
    expect(
      rules('<Input placeholder="https://www.linkedin.com/company/a/b" />'),
    ).toEqual(['long-placeholder:warn']);
    expect(
      rules(
        "const f = { fieldProps: { placeholder: '订单编号、PI 编号、客户、型号' } };",
      ),
    ).toEqual(['long-placeholder:warn']);
  });

  it('写明原因的例外放行，没写原因的例外报错', () => {
    expect(
      rules(
        "// lint-ui-ignore hardcoded-color: 品牌官方色\nconst a = { color: '#E60012' };",
      ),
    ).toEqual([]);
    expect(
      rules(
        "const a = { color: '#E60012' }; // lint-ui-ignore hardcoded-color: 品牌官方色",
      ),
    ).toEqual([]);
    expect(
      rules(
        "// lint-ui-ignore hardcoded-color\nconst a = { color: '#E60012' };",
      ),
    ).toEqual(['ignore-invalid:error']);
    // 例外只管它写明的规则
    expect(
      rules(
        "// lint-ui-ignore gradient: 遮罩\nconst a = { color: '#E60012' };",
      ),
    ).toContain('hardcoded-color:error');
  });

  it('JSX 里写的例外同样放行', () => {
    expect(
      rules(
        [
          '{/* lint-ui-ignore gradient: 两端同色的遮罩 */}',
          "<div style={{ background: 'linear-gradient(#000, #000)' }} />",
        ].join('\n'),
      ),
    ).toEqual(['hardcoded-color:error']);
  });

  it('主题色浅底：写死的报错，条件选中的和写了例外的放行（D42）', () => {
    expect(
      rules('const a = { background: palette.primarySoft, fontSize: 12 };'),
    ).toContain('primary-soft:error');
    expect(
      rules(
        'const a = { background: on ? palette.primarySoft : palette.inset };',
      ),
    ).not.toContain('primary-soft:error');
    expect(
      rules(
        'const a = { background: palette.primarySoft }; // lint-ui-ignore primary-soft: 批量操作条',
      ),
    ).not.toContain('primary-soft:error');
  });

  it('进度条只用主题色：写死颜色、换成功绿的报错', () => {
    expect(rules('<Progress strokeColor={palette.green} />')).toContain(
      'progress-color:error',
    );
    expect(
      rules("<Progress strokeColor={{ from: '#3B82F6', to: '#22D3EE' }} />"),
    ).toContain('progress-color:error');
    expect(rules('<Progress strokeColor={palette.chart} />')).not.toContain(
      'progress-color:error',
    );
  });

  it('注释里的说法不检查', () => {
    expect(
      rules('// 原来是 linear-gradient(...) 和 #2563EB，已改\nconst a = 1;'),
    ).toEqual([]);
  });
});

describe('lint:ui 复审补充', () => {
  it('豁免范围：带缩进、单行、属性里有 JSX 或箭头函数', () => {
    expect(rules('  <Alert message="保存后生效" />')).toEqual([]);
    expect(rules('{show && <Alert message="点击查看详情" />}')).toEqual([]);
    expect(rules("  modal.confirm({ content: '点击进入列表' })")).toEqual([]);
    expect(
      rules('<Alert icon={<Info />} message="点击进入对应列表" />'),
    ).toEqual([]);
    expect(
      rules('<Alert action={<Button>去处理</Button>} message="保存后生效" />'),
    ).toEqual([]);
    expect(
      rules(
        "confirm({ title: '{', content: '点击进入' });\n<span>点击进入对应列表</span>",
      ),
    ).toEqual(['filler:error']);
    // 自闭合的 Alert 之后的内容照查
    expect(
      rules(
        [
          '<Alert onClose={() => setOpen(false)} message="提示" />',
          '<span>点击进入对应列表</span>',
          '<Alert>说明</Alert>',
        ].join('\n'),
      ),
    ).toEqual(['filler:error']);
  });

  it('颜色：锚点、链接、注释不算；模板字符串里的算', () => {
    expect(rules('<a href="#bad">锚点</a>')).toEqual([]);
    expect(rules("const a = { to: '#add-item' };")).toEqual([]);
    expect(rules("const s = '#add-item';")).toEqual([]);
    expect(rules('const a = 1; // 原来是 #1677ff')).toEqual([]);
    expect(rules('<div>{/* #1677ff */}</div>')).toEqual([]);
    expect(rules('const b = `1px solid #E5E8EB`;')).toContain(
      'hardcoded-color:error',
    );
    expect(rules("const c = { color: 'hsl(210, 50%, 40%)' };")).toContain(
      'hardcoded-color:error',
    );
  });

  it('套话的常见变体', () => {
    expect(rules('<span>点击数字进入对应列表处理</span>')).toContain(
      'filler:error',
    );
    expect(rules('<span>点击数字进入对应页面</span>')).toContain(
      'filler:error',
    );
  });

  it('例外：行尾的只管本行；规则名写错、没用上的例外报出来', () => {
    expect(
      rules(
        [
          "const a = { color: '#E60012' }; // lint-ui-ignore hardcoded-color: 品牌色",
          "const b = { color: '#E60012' };",
        ].join('\n'),
      ),
    ).toEqual(['hardcoded-color:error']);
    expect(
      rules(
        "// lint-ui-ignore hardcoded-colour: 品牌色\nconst a = { color: '#E60012' };",
      ),
    ).toEqual(['ignore-invalid:error', 'hardcoded-color:error']);
    expect(rules('// lint-ui-ignore gradient: 遮罩\nconst a = 1;')).toEqual([
      'ignore-unused:warn',
    ]);
  });

  it('旧页头：各业务域包装的 *PageTitle、带说明的 PageHeader、旧位置导入；usePageTitle 不算', () => {
    expect(
      rules('<WarehousePageTitle title="采购入库" description="说明" />'),
    ).toContain('old-header:error');
    expect(rules('<PageHeader title="品牌" description="说明" />')).toContain(
      'old-header:error',
    );
    expect(rules("import { PageHeader } from '../theme';")).toContain(
      'old-header:error',
    );
    expect(
      rules(
        'import { PageHeader } from \'@/components/ListLayout\';\n<PageHeader title="品牌" />',
      ),
    ).toEqual([]);
    expect(
      rules(
        "import { usePageTitle } from '@/components/PageTabs/hooks';\nusePageTitle('销售订单');",
      ),
    ).toEqual([]);
  });
});
