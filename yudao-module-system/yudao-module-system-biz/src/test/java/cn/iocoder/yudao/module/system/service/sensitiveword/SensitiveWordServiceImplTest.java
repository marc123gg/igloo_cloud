package cn.iocoder.yudao.module.system.service.sensitiveword;

import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.collection.SetUtils;
import cn.iocoder.yudao.framework.common.util.date.DateUtils;
import cn.iocoder.yudao.framework.test.core.ut.BaseDbUnitTest;
import cn.iocoder.yudao.module.system.controller.admin.sensitiveword.vo.SensitiveWordCreateReqVO;
import cn.iocoder.yudao.module.system.controller.admin.sensitiveword.vo.SensitiveWordExportReqVO;
import cn.iocoder.yudao.module.system.controller.admin.sensitiveword.vo.SensitiveWordPageReqVO;
import cn.iocoder.yudao.module.system.controller.admin.sensitiveword.vo.SensitiveWordUpdateReqVO;
import cn.iocoder.yudao.module.system.dal.dataobject.sensitiveword.SensitiveWordDO;
import cn.iocoder.yudao.module.system.dal.mysql.sensitiveword.SensitiveWordMapper;
import cn.iocoder.yudao.module.system.mq.producer.sensitiveword.SensitiveWordProducer;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static cn.iocoder.yudao.framework.common.util.date.DateUtils.buildLocalDateTime;
import static cn.iocoder.yudao.framework.common.util.object.ObjectUtils.cloneIgnoreId;
import static cn.iocoder.yudao.framework.common.util.object.ObjectUtils.max;
import static cn.iocoder.yudao.framework.test.core.util.AssertUtils.assertPojoEquals;
import static cn.iocoder.yudao.framework.test.core.util.AssertUtils.assertServiceException;
import static cn.iocoder.yudao.framework.test.core.util.RandomUtils.randomLongId;
import static cn.iocoder.yudao.framework.test.core.util.RandomUtils.randomPojo;
import static cn.iocoder.yudao.module.system.enums.ErrorCodeConstants.SENSITIVE_WORD_NOT_EXISTS;
import static java.util.Collections.singletonList;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verify;

/**
 * {@link SensitiveWordServiceImpl} ??????????????????
 *
 * @author ????????????
 */
@Import(SensitiveWordServiceImpl.class)
public class SensitiveWordServiceImplTest extends BaseDbUnitTest {

    @Resource
    private SensitiveWordServiceImpl sensitiveWordService;

    @Resource
    private SensitiveWordMapper sensitiveWordMapper;

    @MockBean
    private SensitiveWordProducer sensitiveWordProducer;

    @Test
    public void testInitLocalCache() {
        SensitiveWordDO wordDO1 = randomPojo(SensitiveWordDO.class, o -> o.setName("??????")
                .setTags(singletonList("??????")).setStatus(CommonStatusEnum.ENABLE.getStatus()));
        sensitiveWordMapper.insert(wordDO1);
        SensitiveWordDO wordDO2 = randomPojo(SensitiveWordDO.class, o -> o.setName("??????")
                .setTags(singletonList("??????")).setStatus(CommonStatusEnum.ENABLE.getStatus()));
        sensitiveWordMapper.insert(wordDO2);

        // ??????
        sensitiveWordService.initLocalCache();
        // ?????? maxUpdateTime ??????
        assertEquals(max(wordDO1.getUpdateTime(), wordDO2.getUpdateTime()), sensitiveWordService.getMaxUpdateTime());
        // ?????? sensitiveWordTagsCache ??????
        assertEquals(SetUtils.asSet("??????", "??????"), sensitiveWordService.getSensitiveWordTags());
        // ?????? tagSensitiveWordTries ??????
        assertNotNull(sensitiveWordService.getDefaultSensitiveWordTrie());
        assertEquals(2, sensitiveWordService.getTagSensitiveWordTries().size());
        assertNotNull(sensitiveWordService.getTagSensitiveWordTries().get("??????"));
        assertNotNull(sensitiveWordService.getTagSensitiveWordTries().get("??????"));
    }

    @Test
    public void testCreateSensitiveWord_success() {
        // ????????????
        SensitiveWordCreateReqVO reqVO = randomPojo(SensitiveWordCreateReqVO.class);

        // ??????
        Long sensitiveWordId = sensitiveWordService.createSensitiveWord(reqVO);
        // ??????
        assertNotNull(sensitiveWordId);
        // ?????????????????????????????????
        SensitiveWordDO sensitiveWord = sensitiveWordMapper.selectById(sensitiveWordId);
        assertPojoEquals(reqVO, sensitiveWord);
        verify(sensitiveWordProducer).sendSensitiveWordRefreshMessage();
    }

    @Test
    public void testUpdateSensitiveWord_success() {
        // mock ??????
        SensitiveWordDO dbSensitiveWord = randomPojo(SensitiveWordDO.class);
        sensitiveWordMapper.insert(dbSensitiveWord);// @Sql: ?????????????????????????????????
        // ????????????
        SensitiveWordUpdateReqVO reqVO = randomPojo(SensitiveWordUpdateReqVO.class, o -> {
            o.setId(dbSensitiveWord.getId()); // ??????????????? ID
        });

        // ??????
        sensitiveWordService.updateSensitiveWord(reqVO);
        // ????????????????????????
        SensitiveWordDO sensitiveWord = sensitiveWordMapper.selectById(reqVO.getId()); // ???????????????
        assertPojoEquals(reqVO, sensitiveWord);
        verify(sensitiveWordProducer).sendSensitiveWordRefreshMessage();
    }

    @Test
    public void testUpdateSensitiveWord_notExists() {
        // ????????????
        SensitiveWordUpdateReqVO reqVO = randomPojo(SensitiveWordUpdateReqVO.class);

        // ??????, ???????????????
        assertServiceException(() -> sensitiveWordService.updateSensitiveWord(reqVO), SENSITIVE_WORD_NOT_EXISTS);
    }

    @Test
    public void testDeleteSensitiveWord_success() {
        // mock ??????
        SensitiveWordDO dbSensitiveWord = randomPojo(SensitiveWordDO.class);
        sensitiveWordMapper.insert(dbSensitiveWord);// @Sql: ?????????????????????????????????
        // ????????????
        Long id = dbSensitiveWord.getId();

        // ??????
        sensitiveWordService.deleteSensitiveWord(id);
        // ????????????????????????
        assertNull(sensitiveWordMapper.selectById(id));
        verify(sensitiveWordProducer).sendSensitiveWordRefreshMessage();
    }

    @Test
    public void testDeleteSensitiveWord_notExists() {
        // ????????????
        Long id = randomLongId();

        // ??????, ???????????????
        assertServiceException(() -> sensitiveWordService.deleteSensitiveWord(id), SENSITIVE_WORD_NOT_EXISTS);
    }

    @Test
    public void testGetSensitiveWordPage() {
        // mock ??????
        SensitiveWordDO dbSensitiveWord = randomPojo(SensitiveWordDO.class, o -> { // ???????????????
            o.setName("??????");
            o.setTags(Arrays.asList("??????", "??????"));
            o.setStatus(CommonStatusEnum.ENABLE.getStatus());
            o.setCreateTime(DateUtils.buildLocalDateTime(2022, 2, 8));
        });
        sensitiveWordMapper.insert(dbSensitiveWord);
        // ?????? name ?????????
        sensitiveWordMapper.insert(cloneIgnoreId(dbSensitiveWord, o -> o.setName("??????")));
        // ?????? tags ?????????
        sensitiveWordMapper.insert(cloneIgnoreId(dbSensitiveWord, o -> o.setTags(Arrays.asList("??????", "?????????"))));
        // ?????? createTime ?????????
        sensitiveWordMapper.insert(cloneIgnoreId(dbSensitiveWord, o -> o.setCreateTime(DateUtils.buildLocalDateTime(2022, 2, 16))));
        // ????????????
        SensitiveWordPageReqVO reqVO = new SensitiveWordPageReqVO();
        reqVO.setName("???");
        reqVO.setTag("??????");
        reqVO.setStatus(CommonStatusEnum.ENABLE.getStatus());
        reqVO.setCreateTime((new LocalDateTime[]{buildLocalDateTime(2022, 2, 1),buildLocalDateTime(2022, 2, 12)}));

        // ??????
        PageResult<SensitiveWordDO> pageResult = sensitiveWordService.getSensitiveWordPage(reqVO);
        // ??????
        assertEquals(1, pageResult.getTotal());
        assertEquals(1, pageResult.getList().size());
        assertPojoEquals(dbSensitiveWord, pageResult.getList().get(0));
    }

    @Test
    public void testGetSensitiveWordList() {
        // mock ??????
        SensitiveWordDO dbSensitiveWord = randomPojo(SensitiveWordDO.class, o -> { // ???????????????
            o.setName("??????");
            o.setTags(Arrays.asList("??????", "??????"));
            o.setStatus(CommonStatusEnum.ENABLE.getStatus());
            o.setCreateTime(DateUtils.buildLocalDateTime(2022, 2, 8));
        });
        sensitiveWordMapper.insert(dbSensitiveWord);
        // ?????? name ?????????
        sensitiveWordMapper.insert(cloneIgnoreId(dbSensitiveWord, o -> o.setName("??????")));
        // ?????? tags ?????????
        sensitiveWordMapper.insert(cloneIgnoreId(dbSensitiveWord, o -> o.setTags(Arrays.asList("??????", "?????????"))));
        // ?????? createTime ?????????
        sensitiveWordMapper.insert(cloneIgnoreId(dbSensitiveWord, o -> o.setCreateTime(DateUtils.buildLocalDateTime(2022, 2, 16))));
        // ????????????
        SensitiveWordExportReqVO reqVO = new SensitiveWordExportReqVO();
        reqVO.setName("???");
        reqVO.setTag("??????");
        reqVO.setStatus(CommonStatusEnum.ENABLE.getStatus());
        reqVO.setCreateTime((new LocalDateTime[]{buildLocalDateTime(2022, 2, 1),buildLocalDateTime(2022, 2, 12)}));

        // ??????
        List<SensitiveWordDO> list = sensitiveWordService.getSensitiveWordList(reqVO);
        // ??????
        assertEquals(1, list.size());
        assertPojoEquals(dbSensitiveWord, list.get(0));
    }

    @Test
    public void testValidateText_noTag() {
        testInitLocalCache();
        // ????????????
        String text = "???????????????????????????";

        // ??????
        List<String> result = sensitiveWordService.validateText(text, null);
        // ??????
        assertEquals(Arrays.asList("??????", "??????"), result);
    }

    @Test
    public void testValidateText_hasTag() {
        testInitLocalCache();
        // ????????????
        String text = "???????????????????????????";

        // ??????
        List<String> result = sensitiveWordService.validateText(text, singletonList("??????"));
        // ??????
        assertEquals(singletonList("??????"), result);
    }

    @Test
    public void testIsTestValid_noTag() {
        testInitLocalCache();
        // ????????????
        String text = "???????????????????????????";

        // ???????????????
        assertFalse(sensitiveWordService.isTextValid(text, null));
    }

    @Test
    public void testIsTestValid_hasTag() {
        testInitLocalCache();
        // ????????????
        String text = "???????????????????????????";

        // ???????????????
        assertFalse(sensitiveWordService.isTextValid(text, singletonList("??????")));
    }

}
