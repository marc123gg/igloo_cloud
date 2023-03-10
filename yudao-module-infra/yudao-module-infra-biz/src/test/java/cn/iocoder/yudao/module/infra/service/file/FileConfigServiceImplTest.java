package cn.iocoder.yudao.module.infra.service.file;

import cn.hutool.core.date.DatePattern;
import cn.hutool.core.date.LocalDateTimeUtil;
import cn.hutool.core.map.MapUtil;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.file.core.client.FileClient;
import cn.iocoder.yudao.framework.file.core.client.FileClientConfig;
import cn.iocoder.yudao.framework.file.core.client.FileClientFactory;
import cn.iocoder.yudao.framework.file.core.client.local.LocalFileClientConfig;
import cn.iocoder.yudao.framework.file.core.enums.FileStorageEnum;
import cn.iocoder.yudao.framework.test.core.ut.BaseDbUnitTest;
import cn.iocoder.yudao.module.infra.controller.admin.file.vo.config.FileConfigCreateReqVO;
import cn.iocoder.yudao.module.infra.controller.admin.file.vo.config.FileConfigPageReqVO;
import cn.iocoder.yudao.module.infra.controller.admin.file.vo.config.FileConfigUpdateReqVO;
import cn.iocoder.yudao.module.infra.dal.dataobject.file.FileConfigDO;
import cn.iocoder.yudao.module.infra.dal.mysql.file.FileConfigMapper;
import cn.iocoder.yudao.module.infra.mq.producer.file.FileConfigProducer;
import lombok.Data;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;

import javax.annotation.Resource;
import javax.validation.Validator;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Map;

import static cn.hutool.core.util.RandomUtil.randomEle;
import static cn.iocoder.yudao.framework.common.util.date.DateUtils.buildLocalDateTime;
import static cn.iocoder.yudao.framework.common.util.object.ObjectUtils.cloneIgnoreId;
import static cn.iocoder.yudao.framework.common.util.object.ObjectUtils.max;
import static cn.iocoder.yudao.framework.test.core.util.AssertUtils.assertPojoEquals;
import static cn.iocoder.yudao.framework.test.core.util.AssertUtils.assertServiceException;
import static cn.iocoder.yudao.framework.test.core.util.RandomUtils.randomLongId;
import static cn.iocoder.yudao.framework.test.core.util.RandomUtils.randomPojo;
import static cn.iocoder.yudao.module.infra.enums.ErrorCodeConstants.FILE_CONFIG_DELETE_FAIL_MASTER;
import static cn.iocoder.yudao.module.infra.enums.ErrorCodeConstants.FILE_CONFIG_NOT_EXISTS;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
* {@link FileConfigServiceImpl} ??????????????????
*
* @author ????????????
*/
@Import(FileConfigServiceImpl.class)
public class FileConfigServiceImplTest extends BaseDbUnitTest {

    @Resource
    private FileConfigServiceImpl fileConfigService;

    @Resource
    private FileConfigMapper fileConfigMapper;

    @MockBean
    private FileConfigProducer fileConfigProducer;
    @MockBean
    private Validator validator;
    @MockBean
    private FileClientFactory fileClientFactory;

    @Test
    public void testInitLocalCache() {
        // mock ??????
        FileConfigDO configDO1 = randomFileConfigDO().setId(1L).setMaster(true);
        fileConfigMapper.insert(configDO1);
        FileConfigDO configDO2 = randomFileConfigDO().setId(2L).setMaster(false);
        fileConfigMapper.insert(configDO2);
        // mock fileClientFactory ?????? master
        FileClient masterFileClient = mock(FileClient.class);
        when(fileClientFactory.getFileClient(eq(1L))).thenReturn(masterFileClient);

        // ??????
        fileConfigService.initFileClients();
        // ?????? fileClientFactory ??????
        verify(fileClientFactory).createOrUpdateFileClient(eq(1L),
                eq(configDO1.getStorage()), eq(configDO1.getConfig()));
        verify(fileClientFactory).createOrUpdateFileClient(eq(2L),
                eq(configDO2.getStorage()), eq(configDO2.getConfig()));
        assertSame(masterFileClient, fileConfigService.getMasterFileClient());
        // ?????? maxUpdateTime ??????
        assertEquals(max(configDO1.getUpdateTime(), configDO2.getUpdateTime()),
                fileConfigService.getMaxUpdateTime());
    }

    @Test
    public void testCreateFileConfig_success() {
        // ????????????
        Map<String, Object> config = MapUtil.<String, Object>builder().put("basePath", "/yunai")
                .put("domain", "https://www.iocoder.cn").build();
        FileConfigCreateReqVO reqVO = randomPojo(FileConfigCreateReqVO.class,
                o -> o.setStorage(FileStorageEnum.LOCAL.getStorage()).setConfig(config));

        // ??????
        Long fileConfigId = fileConfigService.createFileConfig(reqVO);
        // ??????
        assertNotNull(fileConfigId);
        // ?????????????????????????????????
        FileConfigDO fileConfig = fileConfigMapper.selectById(fileConfigId);
        assertPojoEquals(reqVO, fileConfig, "config");
        assertFalse(fileConfig.getMaster());
        assertEquals("/yunai", ((LocalFileClientConfig) fileConfig.getConfig()).getBasePath());
        assertEquals("https://www.iocoder.cn", ((LocalFileClientConfig) fileConfig.getConfig()).getDomain());
        // verify ??????
        verify(fileConfigProducer).sendFileConfigRefreshMessage();
    }

    @Test
    public void testUpdateFileConfig_success() {
        // mock ??????
        FileConfigDO dbFileConfig = randomPojo(FileConfigDO.class, o -> o.setStorage(FileStorageEnum.LOCAL.getStorage())
                .setConfig(new LocalFileClientConfig().setBasePath("/yunai").setDomain("https://www.iocoder.cn")));
        fileConfigMapper.insert(dbFileConfig);// @Sql: ?????????????????????????????????
        // ????????????
        FileConfigUpdateReqVO reqVO = randomPojo(FileConfigUpdateReqVO.class, o -> {
            o.setId(dbFileConfig.getId()); // ??????????????? ID
            Map<String, Object> config = MapUtil.<String, Object>builder().put("basePath", "/yunai2")
                    .put("domain", "https://doc.iocoder.cn").build();
            o.setConfig(config);
        });

        // ??????
        fileConfigService.updateFileConfig(reqVO);
        // ????????????????????????
        FileConfigDO fileConfig = fileConfigMapper.selectById(reqVO.getId()); // ???????????????
        assertPojoEquals(reqVO, fileConfig, "config");
        assertEquals("/yunai2", ((LocalFileClientConfig) fileConfig.getConfig()).getBasePath());
        assertEquals("https://doc.iocoder.cn", ((LocalFileClientConfig) fileConfig.getConfig()).getDomain());
        // verify ??????
        verify(fileConfigProducer).sendFileConfigRefreshMessage();
    }

    @Test
    public void testUpdateFileConfig_notExists() {
        // ????????????
        FileConfigUpdateReqVO reqVO = randomPojo(FileConfigUpdateReqVO.class);

        // ??????, ???????????????
        assertServiceException(() -> fileConfigService.updateFileConfig(reqVO), FILE_CONFIG_NOT_EXISTS);
    }

    @Test
    public void testUpdateFileConfigMaster_success() {
        // mock ??????
        FileConfigDO dbFileConfig = randomFileConfigDO().setMaster(false);
        fileConfigMapper.insert(dbFileConfig);// @Sql: ?????????????????????????????????
        FileConfigDO masterFileConfig = randomFileConfigDO().setMaster(true);
        fileConfigMapper.insert(masterFileConfig);// @Sql: ?????????????????????????????????

        // ??????
        fileConfigService.updateFileConfigMaster(dbFileConfig.getId());
        // ????????????
        assertTrue(fileConfigMapper.selectById(dbFileConfig.getId()).getMaster());
        assertFalse(fileConfigMapper.selectById(masterFileConfig.getId()).getMaster());
        // verify ??????
        verify(fileConfigProducer).sendFileConfigRefreshMessage();
    }

    @Test
    public void testUpdateFileConfigMaster_notExists() {
        // ??????, ???????????????
        assertServiceException(() -> fileConfigService.updateFileConfigMaster(randomLongId()), FILE_CONFIG_NOT_EXISTS);
    }

    @Test
    public void testDeleteFileConfig_success() {
        // mock ??????
        FileConfigDO dbFileConfig = randomFileConfigDO().setMaster(false);
        fileConfigMapper.insert(dbFileConfig);// @Sql: ?????????????????????????????????
        // ????????????
        Long id = dbFileConfig.getId();

        // ??????
        fileConfigService.deleteFileConfig(id);
       // ????????????????????????
       assertNull(fileConfigMapper.selectById(id));
        // verify ??????
        verify(fileConfigProducer).sendFileConfigRefreshMessage();
    }

    @Test
    public void testDeleteFileConfig_notExists() {
        // ????????????
        Long id = randomLongId();

        // ??????, ???????????????
        assertServiceException(() -> fileConfigService.deleteFileConfig(id), FILE_CONFIG_NOT_EXISTS);
    }

    @Test
    public void testDeleteFileConfig_master() {
        // mock ??????
        FileConfigDO dbFileConfig = randomFileConfigDO().setMaster(true);
        fileConfigMapper.insert(dbFileConfig);// @Sql: ?????????????????????????????????
        // ????????????
        Long id = dbFileConfig.getId();

        // ??????, ???????????????
        assertServiceException(() -> fileConfigService.deleteFileConfig(id), FILE_CONFIG_DELETE_FAIL_MASTER);
    }

    @Test
    public void testGetFileConfigPage() {
       // mock ??????
       FileConfigDO dbFileConfig = randomFileConfigDO().setName("????????????")
               .setStorage(FileStorageEnum.LOCAL.getStorage());
       dbFileConfig.setCreateTime(LocalDateTimeUtil.parse("2020-01-23", DatePattern.NORM_DATE_PATTERN));// ???????????????
       fileConfigMapper.insert(dbFileConfig);
       // ?????? name ?????????
       fileConfigMapper.insert(cloneIgnoreId(dbFileConfig, o -> o.setName("??????")));
       // ?????? storage ?????????
       fileConfigMapper.insert(cloneIgnoreId(dbFileConfig, o -> o.setStorage(FileStorageEnum.DB.getStorage())));
       // ?????? createTime ?????????
       fileConfigMapper.insert(cloneIgnoreId(dbFileConfig, o -> o.setCreateTime(LocalDateTimeUtil.parse("2020-11-23", DatePattern.NORM_DATE_PATTERN))));
       // ????????????
       FileConfigPageReqVO reqVO = new FileConfigPageReqVO();
       reqVO.setName("??????");
       reqVO.setStorage(FileStorageEnum.LOCAL.getStorage());
       reqVO.setCreateTime((new LocalDateTime[]{buildLocalDateTime(2020, 1, 1),
               buildLocalDateTime(2020, 1, 24)}));

       // ??????
       PageResult<FileConfigDO> pageResult = fileConfigService.getFileConfigPage(reqVO);
       // ??????
       assertEquals(1, pageResult.getTotal());
       assertEquals(1, pageResult.getList().size());
       assertPojoEquals(dbFileConfig, pageResult.getList().get(0));
    }

    @Test
    public void testFileConfig() throws Exception {
        // mock ??????
        FileConfigDO dbFileConfig = randomFileConfigDO().setMaster(false);
        fileConfigMapper.insert(dbFileConfig);// @Sql: ?????????????????????????????????
        // ????????????
        Long id = dbFileConfig.getId();
        // mock ?????? Client
        FileClient fileClient = mock(FileClient.class);
        when(fileClientFactory.getFileClient(eq(id))).thenReturn(fileClient);
        when(fileClient.upload(any(), any(), any())).thenReturn("https://www.iocoder.cn");

        // ??????????????????
        assertEquals("https://www.iocoder.cn", fileConfigService.testFileConfig(id));
    }

    private FileConfigDO randomFileConfigDO() {
        return randomPojo(FileConfigDO.class).setStorage(randomEle(FileStorageEnum.values()).getStorage())
                .setConfig(new EmptyFileClientConfig());
    }

    @Data
    public static class EmptyFileClientConfig implements FileClientConfig, Serializable {

    }

}
