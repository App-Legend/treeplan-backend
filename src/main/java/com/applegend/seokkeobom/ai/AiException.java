package com.applegend.seokkeobom.ai;

/** AI 생성 실패. message는 화면에 보여 줄 수 있는 한국어 문장이다. */
public class AiException extends RuntimeException {
    public AiException(String message) { super(message); }
    public AiException(String message, Throwable cause) { super(message, cause); }
}
