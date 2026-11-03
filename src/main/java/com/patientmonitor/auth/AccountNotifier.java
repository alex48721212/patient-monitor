package com.patientmonitor.auth;

/** 계정 안내를 사용자에게 전달하는 통로. 실제 메일 서버를 붙일 때 이 구현만 바꾸면 된다 */
public interface AccountNotifier {

    void sendUsername(String email, String username);

    void sendPasswordResetCode(String email, String code);
}
