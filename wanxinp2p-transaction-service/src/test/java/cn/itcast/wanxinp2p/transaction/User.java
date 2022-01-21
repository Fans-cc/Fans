package cn.itcast.wanxinp2p.transaction;

public class User {
    private int age;
    private String uname;
    static int num = 5;

    public User(int age, String uname) {
        this.age = age;
        this.uname = uname;
    }
    public User(){

    }

    public int getAge() {
        return age;
    }

    public void setAge(int age) {
        this.age = age;
    }

    public String getUname() {
        return uname;
    }

    public void setUname(String uname) {
        this.uname = uname;
    }
}
