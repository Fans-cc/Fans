package cn.itcast.wanxinp2p.account;

import cn.itcast.wanxinp2p.common.util.PasswordUtil;
import org.junit.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class Wanxinp2pAccountServiceApplicationTests {

	@Test
	void contextLoads() {

	}

	@Test
	public void getAdmin(){
		String admin = PasswordUtil.generate("admin");
		System.out.println(admin);
	}

}
