package cn.itcast.wanxinp2p.account.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("account")
public class Account {
    @TableId(type = IdType.AUTO)
    private Long id;
    @TableField("username")
    private String userName;
    @TableField("mobile")
    private String mobile;
    @TableField("password")
    private String passWord;
    @TableField("salt")
    private String salt;
    @TableField("status")
    private Integer status;
    @TableField("domain")
    private String domain;
}
