package cn.iocoder.yudao.module.system.mq.message.mail;

import lombok.Data;

import javax.validation.constraints.NotNull;
import java.util.Map;

/**
 * 邮箱发送消息
 *
 * @author 芋道源码
 */
@Data
public class MailSendMessage {

    /**
     * 邮箱地址
     */
    @NotNull(message = "邮箱地址不能为空")
    private String address;
    /**
     * 短信模板编号
     */
    @NotNull(message = "短信模板编号不能为空")
    private String templateCode;
    /**
     * 短信模板参数
     */
    private Map<String, Object> templateParams;

    /**
     * 用户编号，允许空
     */
    private Integer userId;
    /**
     * 用户类型，允许空
     */
    private Integer userType;

}
