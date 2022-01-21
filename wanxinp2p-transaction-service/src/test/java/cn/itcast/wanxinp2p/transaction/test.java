package cn.itcast.wanxinp2p.transaction;

public class test {
    public static void main(String[] args) {
        User user = new User(25,"fanshuai");
        user.num = user.num - 1;
        System.out.println(user.num);
        User user1 = new User(25,"ss");
        user1.num = user1.num - 1;
        System.out.println(user1.num);
        User user2 = new User(25,"cc");
        user2.num = user2.num - 1;
        System.out.println(user2.num);
        User user3 = new User(25,"aa");
        user3.num = user3.num - 1;
        System.out.println(user3.num);
        User user4 = new User(25,"ff");
        user4.num = user4.num - 1;
        System.out.println(user4.num);
    }
}
