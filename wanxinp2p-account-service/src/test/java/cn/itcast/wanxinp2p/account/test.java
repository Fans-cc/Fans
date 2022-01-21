package cn.itcast.wanxinp2p.account;

import cn.itcast.wanxinp2p.common.util.PasswordUtil;

public class test {
    public static void main(String[] args) {
        String admin = PasswordUtil.generate("admin");
        System.out.println(admin);
    }
}
