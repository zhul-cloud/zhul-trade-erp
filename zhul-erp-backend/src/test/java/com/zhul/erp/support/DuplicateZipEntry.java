package com.zhul.erp.support;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * 造一个有重复条目的 OOXML（audit DEP-001，CVE-2025-31672）：复制 {@code entry}，换成 {@code replacement} 内容后改回同名。
 * ZipOutputStream 不允许重名，所以先用同长度的临时名写入，再把文件头与中央目录里的名字改回去（CRC 不含文件名，不受影响）。
 */
public final class DuplicateZipEntry {

    private DuplicateZipEntry() {
    }

    public static byte[] of(byte[] ooxml, String entry, byte[] replacement) throws IOException {
        String temp = entry.substring(0, entry.length() - 1) + "~";
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(ooxml)); ZipOutputStream zip = new ZipOutputStream(out)) {
            for (ZipEntry e = in.getNextEntry(); e != null; e = in.getNextEntry()) {
                zip.putNextEntry(new ZipEntry(e.getName()));
                in.transferTo(zip);
                zip.closeEntry();
            }
            zip.putNextEntry(new ZipEntry(temp));
            zip.write(replacement);
            zip.closeEntry();
        }
        return replaceAll(out.toByteArray(), temp.getBytes(StandardCharsets.UTF_8), entry.getBytes(StandardCharsets.UTF_8));
    }

    /** 把 {@code entry} 的内容换成 {@code replacement}（不重复，模拟内容损坏的文件） */
    public static byte[] replaced(byte[] ooxml, String entry, byte[] replacement) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(ooxml)); ZipOutputStream zip = new ZipOutputStream(out)) {
            for (ZipEntry e = in.getNextEntry(); e != null; e = in.getNextEntry()) {
                zip.putNextEntry(new ZipEntry(e.getName()));
                if (e.getName().equals(entry)) {
                    zip.write(replacement);
                } else {
                    in.transferTo(zip);
                }
                zip.closeEntry();
            }
        }
        return out.toByteArray();
    }

    private static byte[] replaceAll(byte[] data, byte[] from, byte[] to) {
        byte[] result = data.clone();
        for (int i = 0; i <= result.length - from.length; i++) {
            boolean match = true;
            for (int j = 0; j < from.length && match; j++) {
                match = result[i + j] == from[j];
            }
            if (match) {
                System.arraycopy(to, 0, result, i, to.length);
            }
        }
        return result;
    }
}
