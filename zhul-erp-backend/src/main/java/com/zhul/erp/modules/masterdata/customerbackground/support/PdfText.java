package com.zhul.erp.modules.masterdata.customerbackground.support;

import com.zhul.erp.common.exception.BizException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.text.PDFTextStripper;

import java.io.IOException;

/** 上传背调报告的 PDF 文字提取（design 决策 7） */
public final class PdfText {

    public static final String NO_TEXT = "这个 PDF 没有可读文字，请粘贴报告文字";
    /** 去掉空白后少于这么多字，视为扫描件 */
    static final int MIN_CHARS = 20;

    private PdfText() {
    }

    public static String extract(byte[] pdf) {
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            String text = new PDFTextStripper().getText(doc);
            String trimmed = text == null ? "" : text.strip();
            if (trimmed.replaceAll("\\s+", "").length() < MIN_CHARS) {
                throw new BizException(NO_TEXT);
            }
            return trimmed;
        } catch (InvalidPasswordException e) {
            throw new BizException("这个 PDF 设置了密码，请先去掉密码或粘贴报告文字", e);
        } catch (IOException e) {
            throw new BizException("PDF 无法读取，请确认文件完好或粘贴报告文字", e);
        }
    }
}
