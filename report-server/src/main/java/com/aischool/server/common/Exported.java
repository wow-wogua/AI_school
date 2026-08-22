package com.aischool.server.common;

/** 通用导出产物：下载文件名 + 文件字节（xlsx 等） */
public record Exported(String filename, byte[] content) {}
