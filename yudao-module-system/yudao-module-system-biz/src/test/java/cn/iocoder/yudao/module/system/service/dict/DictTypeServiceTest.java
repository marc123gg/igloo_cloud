package cn.iocoder.yudao.module.system.service.dict;

import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.system.controller.admin.dict.vo.type.DictTypeCreateReqVO;
import cn.iocoder.yudao.module.system.controller.admin.dict.vo.type.DictTypeUpdateReqVO;
import cn.iocoder.yudao.module.system.controller.admin.dict.vo.type.DictTypeExportReqVO;
import cn.iocoder.yudao.module.system.controller.admin.dict.vo.type.DictTypePageReqVO;
import cn.iocoder.yudao.module.system.dal.dataobject.dict.DictTypeDO;
import cn.iocoder.yudao.module.system.dal.mysql.dict.DictTypeMapper;
import cn.iocoder.yudao.framework.common.util.collection.ArrayUtils;
import cn.iocoder.yudao.framework.common.util.object.ObjectUtils;
import cn.iocoder.yudao.framework.test.core.ut.BaseDbUnitTest;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Consumer;

import static cn.hutool.core.util.RandomUtil.randomEle;
import static cn.iocoder.yudao.module.system.enums.ErrorCodeConstants.*;
import static cn.iocoder.yudao.framework.test.core.util.AssertUtils.assertPojoEquals;
import static cn.iocoder.yudao.framework.test.core.util.AssertUtils.assertServiceException;
import static cn.iocoder.yudao.framework.common.util.date.DateUtils.buildLocalDateTime;
import static cn.iocoder.yudao.framework.test.core.util.RandomUtils.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@Import(DictTypeServiceImpl.class)
public class DictTypeServiceTest extends BaseDbUnitTest {

    @Resource
    private DictTypeServiceImpl dictTypeService;

    @Resource
    private DictTypeMapper dictTypeMapper;
    @MockBean
    private DictDataService dictDataService;

    @Test
    public void testGetDictTypePage() {
        // mock ??????
        DictTypeDO dbDictType = randomPojo(DictTypeDO.class, o -> { // ???????????????
            o.setName("yunai");
            o.setType("??????");
            o.setStatus(CommonStatusEnum.ENABLE.getStatus());
            o.setCreateTime(buildLocalDateTime(2021, 1, 15));
        });
        dictTypeMapper.insert(dbDictType);
        // ?????? name ?????????
        dictTypeMapper.insert(ObjectUtils.cloneIgnoreId(dbDictType, o -> o.setName("tudou")));
        // ?????? type ?????????
        dictTypeMapper.insert(ObjectUtils.cloneIgnoreId(dbDictType, o -> o.setType("??????")));
        // ?????? status ?????????
        dictTypeMapper.insert(ObjectUtils.cloneIgnoreId(dbDictType, o -> o.setStatus(CommonStatusEnum.DISABLE.getStatus())));
        // ?????? createTime ?????????
        dictTypeMapper.insert(ObjectUtils.cloneIgnoreId(dbDictType, o -> o.setCreateTime(buildLocalDateTime(2021, 1, 1))));
        // ????????????
        DictTypePageReqVO reqVO = new DictTypePageReqVO();
        reqVO.setName("nai");
        reqVO.setType("???");
        reqVO.setStatus(CommonStatusEnum.ENABLE.getStatus());
        reqVO.setCreateTime((new LocalDateTime[]{buildLocalDateTime(2021, 1, 10),buildLocalDateTime(2021, 1, 20)}));

        // ??????
        PageResult<DictTypeDO> pageResult = dictTypeService.getDictTypePage(reqVO);
        // ??????
        assertEquals(1, pageResult.getTotal());
        assertEquals(1, pageResult.getList().size());
        assertPojoEquals(dbDictType, pageResult.getList().get(0));
    }

    @Test
    public void testGetDictTypeList() {
        // mock ??????
        DictTypeDO dbDictType = randomPojo(DictTypeDO.class, o -> { // ???????????????
            o.setName("yunai");
            o.setType("??????");
            o.setStatus(CommonStatusEnum.ENABLE.getStatus());
            o.setCreateTime(buildLocalDateTime(2021, 1, 15));
        });
        dictTypeMapper.insert(dbDictType);
        // ?????? name ?????????
        dictTypeMapper.insert(ObjectUtils.cloneIgnoreId(dbDictType, o -> o.setName("tudou")));
        // ?????? type ?????????
        dictTypeMapper.insert(ObjectUtils.cloneIgnoreId(dbDictType, o -> o.setType("??????")));
        // ?????? status ?????????
        dictTypeMapper.insert(ObjectUtils.cloneIgnoreId(dbDictType, o -> o.setStatus(CommonStatusEnum.DISABLE.getStatus())));
        // ?????? createTime ?????????
        dictTypeMapper.insert(ObjectUtils.cloneIgnoreId(dbDictType, o -> o.setCreateTime(buildLocalDateTime(2021, 1, 1))));
        // ????????????
        DictTypeExportReqVO reqVO = new DictTypeExportReqVO();
        reqVO.setName("nai");
        reqVO.setType("???");
        reqVO.setStatus(CommonStatusEnum.ENABLE.getStatus());
        reqVO.setCreateTime((new LocalDateTime[]{buildLocalDateTime(2021, 1, 10),buildLocalDateTime(2021, 1, 20)}));

        // ??????
        List<DictTypeDO> list = dictTypeService.getDictTypeList(reqVO);
        // ??????
        assertEquals(1, list.size());
        assertPojoEquals(dbDictType, list.get(0));
    }

    @Test
    public void testGetDictType() {
        // mock ??????
        DictTypeDO dbDictType = randomDictTypeDO();
        dictTypeMapper.insert(dbDictType);
        // ????????????
        String type = dbDictType.getType();

        // ??????
        DictTypeDO dictType = dictTypeService.getDictType(type);
        // ??????
        assertNotNull(dictType);
        assertPojoEquals(dbDictType, dictType);
    }

    @Test
    public void testCreateDictType_success() {
        // ????????????
        DictTypeCreateReqVO reqVO = randomPojo(DictTypeCreateReqVO.class,
                o -> o.setStatus(randomEle(CommonStatusEnum.values()).getStatus()));

        // ??????
        Long dictTypeId = dictTypeService.createDictType(reqVO);
        // ??????
        assertNotNull(dictTypeId);
        // ?????????????????????????????????
        DictTypeDO dictType = dictTypeMapper.selectById(dictTypeId);
        assertPojoEquals(reqVO, dictType);
    }

    @Test
    public void testUpdateDictType_success() {
        // mock ??????
        DictTypeDO dbDictType = randomDictTypeDO();
        dictTypeMapper.insert(dbDictType);// @Sql: ?????????????????????????????????
        // ????????????
        DictTypeUpdateReqVO reqVO = randomPojo(DictTypeUpdateReqVO.class, o -> {
            o.setId(dbDictType.getId()); // ??????????????? ID
            o.setStatus(randomEle(CommonStatusEnum.values()).getStatus());
        });

        // ??????
        dictTypeService.updateDictType(reqVO);
        // ????????????????????????
        DictTypeDO dictType = dictTypeMapper.selectById(reqVO.getId()); // ???????????????
        assertPojoEquals(reqVO, dictType);
    }

    @Test
    public void testDeleteDictType_success() {
        // mock ??????
        DictTypeDO dbDictType = randomDictTypeDO();
        dictTypeMapper.insert(dbDictType);// @Sql: ?????????????????????????????????
        // ????????????
        Long id = dbDictType.getId();

        // ??????
        dictTypeService.deleteDictType(id);
        // ????????????????????????
        assertNull(dictTypeMapper.selectById(id));
    }

    @Test
    public void testDeleteDictType_hasChildren() {
        // mock ??????
        DictTypeDO dbDictType = randomDictTypeDO();
        dictTypeMapper.insert(dbDictType);// @Sql: ?????????????????????????????????
        // ????????????
        Long id = dbDictType.getId();
        // mock ??????
        when(dictDataService.countByDictType(eq(dbDictType.getType()))).thenReturn(1L);

        // ??????, ???????????????
        assertServiceException(() -> dictTypeService.deleteDictType(id), DICT_TYPE_HAS_CHILDREN);
    }

    @Test
    public void testCheckDictDataExists_success() {
        // mock ??????
        DictTypeDO dbDictType = randomDictTypeDO();
        dictTypeMapper.insert(dbDictType);// @Sql: ?????????????????????????????????

        // ????????????
        dictTypeService.checkDictTypeExists(dbDictType.getId());
    }

    @Test
    public void testCheckDictDataExists_notExists() {
        assertServiceException(() -> dictTypeService.checkDictTypeExists(randomLongId()), DICT_TYPE_NOT_EXISTS);
    }

    @Test
    public void testCheckDictTypeUnique_success() {
        // ???????????????
        dictTypeService.checkDictTypeUnique(randomLongId(), randomString());
    }

    @Test
    public void testCheckDictTypeUnique_valueDuplicateForCreate() {
        // ????????????
        String type = randomString();
        // mock ??????
        dictTypeMapper.insert(randomDictTypeDO(o -> o.setType(type)));

        // ?????????????????????
        assertServiceException(() -> dictTypeService.checkDictTypeUnique(null, type),
                DICT_TYPE_TYPE_DUPLICATE);
    }

    @Test
    public void testCheckDictTypeUnique_valueDuplicateForUpdate() {
        // ????????????
        Long id = randomLongId();
        String type = randomString();
        // mock ??????
        dictTypeMapper.insert(randomDictTypeDO(o -> o.setType(type)));

        // ?????????????????????
        assertServiceException(() -> dictTypeService.checkDictTypeUnique(id, type),
                DICT_TYPE_TYPE_DUPLICATE);
    }

    @Test
    public void testCheckDictTypNameUnique_success() {
        // ???????????????
        dictTypeService.checkDictTypeNameUnique(randomLongId(), randomString());
    }

    @Test
    public void testCheckDictTypeNameUnique_nameDuplicateForCreate() {
        // ????????????
        String name = randomString();
        // mock ??????
        dictTypeMapper.insert(randomDictTypeDO(o -> o.setName(name)));

        // ?????????????????????
        assertServiceException(() -> dictTypeService.checkDictTypeNameUnique(null, name),
                DICT_TYPE_NAME_DUPLICATE);
    }

    @Test
    public void testCheckDictTypeNameUnique_nameDuplicateForUpdate() {
        // ????????????
        Long id = randomLongId();
        String name = randomString();
        // mock ??????
        dictTypeMapper.insert(randomDictTypeDO(o -> o.setName(name)));

        // ?????????????????????
        assertServiceException(() -> dictTypeService.checkDictTypeNameUnique(id, name),
                DICT_TYPE_NAME_DUPLICATE);
    }

    // ========== ???????????? ==========

    @SafeVarargs
    private static DictTypeDO randomDictTypeDO(Consumer<DictTypeDO>... consumers) {
        Consumer<DictTypeDO> consumer = (o) -> {
            o.setStatus(randomEle(CommonStatusEnum.values()).getStatus()); // ?????? status ?????????
        };
        return randomPojo(DictTypeDO.class, ArrayUtils.append(consumer, consumers));
    }

}
