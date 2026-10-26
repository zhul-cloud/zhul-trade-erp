import { createStyles } from 'antd-style';
import React from 'react';
import { useAppearance } from '@/components/Shell/BrandLogo';

const useStyles = createStyles(({ token, css }) => ({
  footer: css`
    padding: 16px 24px;
    text-align: center;
    color: ${token.colorTextDescription};
    font-size: ${token.fontSizeSM}px;
    line-height: ${token.lineHeight};
    background: transparent;
  `,
  copyright: css`
    margin-bottom: 6px;
  `,
}));

const Footer: React.FC = () => {
  const { styles } = useStyles();
  const year = new Date().getFullYear();
  const { siteName } = useAppearance();

  return (
    <div className={styles.footer}>
      <div className={styles.copyright}>
        {siteName} &copy; {year}
      </div>
    </div>
  );
};

export default Footer;
