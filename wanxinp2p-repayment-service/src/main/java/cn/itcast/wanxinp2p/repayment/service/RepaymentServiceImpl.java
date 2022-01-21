package cn.itcast.wanxinp2p.repayment.service;

import cn.itcast.wanxinp2p.api.depository.model.RepaymentDetailRequest;
import cn.itcast.wanxinp2p.api.depository.model.RepaymentRequest;
import cn.itcast.wanxinp2p.api.depository.model.UserAutoPreTransactionRequest;
import cn.itcast.wanxinp2p.api.repayment.model.ProjectWithTendersDTO;
import cn.itcast.wanxinp2p.api.transaction.model.BorrowerDTO;
import cn.itcast.wanxinp2p.api.transaction.model.ProjectDTO;
import cn.itcast.wanxinp2p.api.transaction.model.TenderDTO;
import cn.itcast.wanxinp2p.common.domain.*;
import cn.itcast.wanxinp2p.common.util.CodeNoUtil;
import cn.itcast.wanxinp2p.common.util.DateUtil;
import cn.itcast.wanxinp2p.repayment.agent.ConsumerApiAgent;
import cn.itcast.wanxinp2p.repayment.agent.DepositoryAgentApiAgent;
import cn.itcast.wanxinp2p.repayment.entity.ReceivableDetail;
import cn.itcast.wanxinp2p.repayment.entity.ReceivablePlan;
import cn.itcast.wanxinp2p.repayment.entity.RepaymentDetail;
import cn.itcast.wanxinp2p.repayment.entity.RepaymentPlan;
import cn.itcast.wanxinp2p.repayment.mapper.PlanMapper;
import cn.itcast.wanxinp2p.repayment.mapper.ReceivableDetailMapper;
import cn.itcast.wanxinp2p.repayment.mapper.ReceivablePlanMapper;
import cn.itcast.wanxinp2p.repayment.mapper.RepaymentDetailMapper;
import cn.itcast.wanxinp2p.repayment.message.RepaymentProducer;
import cn.itcast.wanxinp2p.repayment.model.EqualInterestRepayment;
import cn.itcast.wanxinp2p.repayment.util.RepaymentUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class RepaymentServiceImpl implements RepaymentService{
    @Autowired
    private PlanMapper planMapper;
    @Autowired
    private ReceivablePlanMapper receivablePlanMapper;
    @Autowired
    private RepaymentDetailMapper repaymentDetailMapper;
    @Autowired
    private DepositoryAgentApiAgent depositoryAgentApiAgent;
    @Autowired
    private ReceivableDetailMapper receivableDetailMapper;
    @Autowired
    private RepaymentProducer repaymentProducer;
    @Autowired
    private ConsumerApiAgent consumerApiAgent;
    @Autowired
    private SmsService smsService;
    /**
     * 启动还款
     * 1. 生成借款人还款计划
     * 2. 生成投资人应收明细
     * 3. 保存还款计划到数据库
     * 4. 保存应收明细到数据库
     * @param projectWithTendersDTO
     * @return
     */
    @Override
    @Transactional(rollbackFor = BusinessException.class)
    public String startRepayment(ProjectWithTendersDTO projectWithTendersDTO) {
        //1.生成借款人还款计划
        //1.1. 获取标的信息
        ProjectDTO projectDTO = projectWithTendersDTO.getProject();
        //1.2. 获取投标信息
        List<TenderDTO> tenderDTOS = projectWithTendersDTO.getTenders();
        //1.3 计算还款的月数
        Double ceil = Math.ceil(projectDTO.getPeriod() / 30.0);
        int month = ceil.intValue();
        //1.4 还款方式，只针对等额本息
        String repaymentWay = projectDTO.getRepaymentWay();
        if (repaymentWay.equals(RepaymentWayCode.FIXED_REPAYMENT.getCode())){
            //1.5 生成还款计划
            EqualInterestRepayment equalInterestRepayment = RepaymentUtil.fixedRepayment(projectDTO.getAmount(), projectDTO.getBorrowerAnnualRate(), month, projectDTO.getCommissionAnnualRate());
            //1.6 保存还款计划
            List<RepaymentPlan> repaymentPlanList = saveRepaymentPlan(projectDTO, equalInterestRepayment);

            //2.生成投资人应收明细
            //2.1 根据投标信息生成应收明细
            tenderDTOS.forEach(tenderDTO -> {
                //当前投标人的收款明细
                final EqualInterestRepayment receipt = RepaymentUtil.fixedRepayment(tenderDTO.getAmount(),
                        tenderDTO.getProjectAnnualRate(), month, projectWithTendersDTO .getCommissionInvestorAnnualRate());
                /* 由于投标人的收款明细需要还款信息,所有遍历还款计划, 把还款期数与投资人应收期数对应上*/
                repaymentPlanList.forEach(repaymentPlan -> {
                    saveRreceivablePlan(repaymentPlan,tenderDTO,receipt);
                });

            });


        }else {
            return "-1";
        }

        return DepositoryReturnCode.RETURN_CODE_00000.getCode();
    }

    @Override
    public List<RepaymentPlan> selectDueRepayment(String date,int shardingCount,int shardingItem) {
        return planMapper.selectDueRepayment(date,shardingCount,shardingItem);
    }

    @Override
    public RepaymentDetail saveRepaymentDetail(RepaymentPlan repaymentPlan) {
        RepaymentDetail repaymentDetail = repaymentDetailMapper.selectOne(Wrappers.<RepaymentDetail>lambdaQuery().eq(RepaymentDetail::getRepaymentPlanId, repaymentPlan.getId()));
        if (repaymentDetail == null){
            repaymentDetail = new RepaymentDetail();
            //设置还款计划ID
            repaymentDetail.setRepaymentPlanId(repaymentPlan.getId());
            //实还本息
            repaymentDetail.setAmount(repaymentPlan.getAmount());
            //实际还款时间
            repaymentDetail.setRepaymentDate(LocalDateTime.now());
            //请求流水号
            repaymentDetail.setRequestNo(CodeNoUtil.getNo(CodePrefixCode.CODE_REQUEST_PREFIX));
            //未同步
            repaymentDetail.setStatus(StatusCode.STATUS_OUT.getCode());
            //保存数据
            repaymentDetailMapper.insert(repaymentDetail);
        }
        return repaymentDetail;
    }

    /**
     * 执行还款
     * @param date
     */
    @Override
    public void executeRepayment(String date,int shardingCount,int shardingItem) {
        //第一阶段 查询所有到期的还款计划,生成还款明细(未同步)
        List<RepaymentPlan> repaymentPlanList = selectDueRepayment(date, shardingCount, shardingItem);
        repaymentPlanList.forEach(repaymentPlan -> {
            //打印当前服务器分片
            System.out.println("当前分片: " + shardingItem + "\n" + repaymentPlan);
            //生成还款明细(未同步)
            RepaymentDetail repaymentDetail = saveRepaymentDetail(repaymentPlan);
            String preRequestNo = repaymentDetail.getRequestNo();
            Boolean preRepaymentResult = preRepayment(repaymentPlan, preRequestNo);
            if (preRepaymentResult){
                //构造还款信息请求数据
                RepaymentRequest repaymentRequest = generateRepaymentRequest(repaymentPlan, preRequestNo);
                //发送确认还款事务消息
                repaymentProducer.confirmRepayment(repaymentPlan,repaymentRequest);
            }
        });
    }

    RepaymentRequest generateRepaymentRequest(RepaymentPlan repaymentPlan, String preRequestNo){
        //根据还款计划id, 获取应收计划
        final List<ReceivablePlan> receivablePlanList = receivablePlanMapper.selectList( Wrappers. <ReceivablePlan>lambdaQuery().eq(ReceivablePlan::getRepaymentId, repaymentPlan.getId()));
        //封装请求数据
        RepaymentRequest repaymentRequest = new RepaymentRequest();
        // 还款总额
        repaymentRequest.setAmount(repaymentPlan.getAmount());
        // 业务实体id
        repaymentRequest.setId(repaymentPlan.getId());
        // 向借款人收取的佣金
        repaymentRequest.setCommission(repaymentPlan.getCommission());
        // 标的编码
        repaymentRequest.setProjectNo(repaymentPlan.getProjectNo());
        // 请求流水号
        repaymentRequest.setRequestNo(CodeNoUtil.getNo(CodePrefixCode.CODE_REQUEST_PREFIX));
        // 预处理业务流水号
        repaymentRequest.setPreRequestNo(preRequestNo);
        // 放款明细
        List<RepaymentDetailRequest> detailRequests = new ArrayList<>();
        receivablePlanList.forEach(receivablePlan -> {
            RepaymentDetailRequest detailRequest = new RepaymentDetailRequest();
            // 投资人用户编码
            detailRequest.setUserNo(receivablePlan.getUserNo());
            // 向投资人收取的佣金
            detailRequest.setCommission(receivablePlan.getCommission());
            // 派息 - 无
            // 投资人应得本金
            detailRequest.setAmount(receivablePlan.getPrincipal());
            // 投资人应得利息
            detailRequest.setInterest(receivablePlan.getInterest());
            // 添加到集合
            detailRequests.add(detailRequest);
        });
        // 还款明细请求信息
        repaymentRequest.setDetails(detailRequests);
        return repaymentRequest;
    }

    /**
     * 还款预处理 第二阶段: 冻结借款人的金额
     * @param repaymentPlan
     * @param preRequestNo
     * @return
     */
    @Override
    public Boolean preRepayment(RepaymentPlan repaymentPlan, String preRequestNo) {
        //1. 构造请求数据
        UserAutoPreTransactionRequest userAutoPreTransactionRequest = generateUserAutoPreTransactionRequest(repaymentPlan, preRequestNo);
        //2. 请求存管代理服务
        RestResponse<String> restResponse = depositoryAgentApiAgent.userAutoPreTransaction(userAutoPreTransactionRequest);
        //3. 返回结果
        return restResponse.getResult().equals(DepositoryReturnCode.RETURN_CODE_00000.getCode());
    }

    @Override
    @Transactional
    public Boolean confirmRepayment(RepaymentPlan repaymentPlan, RepaymentRequest repaymentRequest) {
        //1. 更新还款明细：已同步
        String preRequestNo=repaymentRequest.getPreRequestNo();
        repaymentDetailMapper.update(null,Wrappers.<RepaymentDetail>lambdaUpdate()
                .set(RepaymentDetail::getStatus,StatusCode.STATUS_IN.getCode())
                .eq(RepaymentDetail::getRequestNo,preRequestNo));
        //2. 更新receivable_plan表为：已收
        //根据还款计划id，查询应收计划
        List<ReceivablePlan> receivablePlanList = receivablePlanMapper.selectList(Wrappers.<ReceivablePlan>lambdaQuery()
                .eq(ReceivablePlan::getRepaymentId, repaymentPlan.getId()));
        receivablePlanList.forEach(receivablePlan -> {
            receivablePlan.setReceivableStatus(1);
            receivablePlanMapper.updateById(receivablePlan);

            //2.2. 保存数据到receivable_detail
            // 构造应收明细
            ReceivableDetail receivableDetail = new ReceivableDetail();
            // 应收项标识
            receivableDetail.setReceivableId(receivablePlan.getId());
            // 实收本息
            receivableDetail.setAmount(receivablePlan.getAmount());
            // 实收时间
            receivableDetail.setReceivableDate(DateUtil.now());
            // 保存投资人应收明细
            receivableDetailMapper.insert(receivableDetail);
        });

        //3. 更新还款计划：已还款
        repaymentPlan.setRepaymentStatus("1");
        int rows = planMapper.updateById(repaymentPlan);
        return rows>0;
    }

    @Override
    public void invokeConfirmRepayment(RepaymentPlan repaymentPlan, RepaymentRequest repaymentRequest) {
        RestResponse<String> restResponse = depositoryAgentApiAgent.confirmRepayment(repaymentRequest);
        if (!restResponse.getResult().equals(DepositoryReturnCode.RETURN_CODE_00000.getCode())){
            throw new RuntimeException("还款失败");
        }
    }

    @Override
    public void sendRepaymentNotify(String date,int shardingCount,int shardingItem) {
        //第一阶段 查询所有到期的还款计划,生成还款明细(未同步)
        List<RepaymentPlan> repaymentPlanList = selectDueRepayment(date, shardingCount, shardingItem);
        repaymentPlanList.forEach(repaymentPlan -> {
            //打印当前服务器分片
            System.out.println("当前分片: " + shardingItem + "\n" + repaymentPlan);
            //得到还款人的信息
            RestResponse<BorrowerDTO> borrowerMobile = consumerApiAgent.getBorrowerMobile(repaymentPlan.getConsumerId());
            //发送还款短信
            smsService.sendRepaymentNotify(borrowerMobile.getResult().getMobile(),date,repaymentPlan.getAmount());

        });
    }

    public UserAutoPreTransactionRequest generateUserAutoPreTransactionRequest(RepaymentPlan repaymentPlan , String preRequestNo){
        UserAutoPreTransactionRequest userAutoPreTransactionRequest = new UserAutoPreTransactionRequest();
        //冻结金额
        userAutoPreTransactionRequest.setAmount(repaymentPlan.getAmount());
        // 预处理业务类型
        userAutoPreTransactionRequest.setBizType(PreprocessBusinessTypeCode.REPAYMENT.getCode());
        // 标的号
        userAutoPreTransactionRequest.setProjectNo(repaymentPlan.getProjectNo());
        // 请求流水号
        userAutoPreTransactionRequest.setRequestNo(preRequestNo);
        // 标的用户编码
        userAutoPreTransactionRequest.setUserNo(repaymentPlan.getUserNo());
        // 关联业务实体标识
        userAutoPreTransactionRequest.setId(repaymentPlan.getId());
        // 返回结果
        return userAutoPreTransactionRequest;
    }

    /**
     * 保存还款计划到数据库
     * @param projectDTO
     * @param equalInterestRepayment
     * @return
     */
    public List<RepaymentPlan> saveRepaymentPlan(ProjectDTO projectDTO , EqualInterestRepayment equalInterestRepayment){

        List<RepaymentPlan> repaymentPlanList = new ArrayList<>();
        // 获取每期利息
        Map<Integer, BigDecimal> interestMap = equalInterestRepayment.getInterestMap();
        // 平台收取利息
        Map<Integer, BigDecimal> commissionMap = equalInterestRepayment.getCommissionMap();
        // 获取每期本金
        equalInterestRepayment.getPrincipalMap().forEach((k,v) ->{
            // 还款计划封装数据
            final RepaymentPlan repaymentPlan = new RepaymentPlan();
            // 标的id
            repaymentPlan.setProjectId(projectDTO.getId());
            // 发标人用户标识
            repaymentPlan.setConsumerId(projectDTO.getConsumerId());
            // 发标人用户编码
            repaymentPlan.setUserNo(projectDTO.getUserNo());
            // 标的编码
            repaymentPlan.setProjectNo(projectDTO.getProjectNo());
            // 期数
            repaymentPlan.setNumberOfPeriods(k);
            // 当期还款利息
            repaymentPlan.setInterest(interestMap.get(k));
            // 还款本金
            repaymentPlan.setPrincipal(v);
            // 本息
            repaymentPlan.setAmount(repaymentPlan.getPrincipal().add(repaymentPlan.getInterest()));
            // 应还时间 = 当前时间 + 期数(月)
            repaymentPlan.setShouldRepaymentDate(DateUtil.localDateTimeAddMonth(LocalDateTime.now(),k));
            // 应还状态
            repaymentPlan.setRepaymentStatus("0");
            // 计划创建时间
            repaymentPlan.setCreateDate(LocalDateTime.now());
            // 设置平台佣金( 借款人让利 ) 注意这个地方是 具体佣金
            repaymentPlan.setCommission(commissionMap.get(k));
            // 保存到数据库
            planMapper.insert(repaymentPlan);
            repaymentPlanList.add(repaymentPlan);
        });
            return repaymentPlanList;
    }

    /**
     * 保存应收明细到数据库
     * @param repaymentPlan
     * @param tenderDTO
     * @param equalInterestRepayment
     */
    public void saveRreceivablePlan(RepaymentPlan repaymentPlan , TenderDTO tenderDTO , EqualInterestRepayment equalInterestRepayment){
        // 获取每期利息
        Map<Integer, BigDecimal> interestMap = equalInterestRepayment.getInterestMap();
        // 平台收取利息
        Map<Integer, BigDecimal> commissionMap = equalInterestRepayment.getCommissionMap();
        // 获取每期本金
        Map<Integer, BigDecimal> principalMap = equalInterestRepayment.getPrincipalMap();
        // 封装投资人应收明细
        ReceivablePlan receivablePlan = new ReceivablePlan();
        // 投标信息标识
        receivablePlan.setTenderId(tenderDTO.getId());
        // 设置期数
        receivablePlan.setNumberOfPeriods(repaymentPlan.getNumberOfPeriods());
        // 投标人用户标识
        receivablePlan.setConsumerId(repaymentPlan.getConsumerId());
        // 投标人用户编码
        receivablePlan.setUserNo(repaymentPlan.getUserNo());
        // 还款计划项标识
        receivablePlan.setRepaymentId(repaymentPlan.getId());
        // 应收利息
        receivablePlan.setInterest(interestMap.get(repaymentPlan.getNumberOfPeriods()));
        // 应收本金
        receivablePlan.setPrincipal(principalMap.get(repaymentPlan .getNumberOfPeriods()));
        // 应收本息 = 应收本金 + 应收利息
        receivablePlan.setAmount(receivablePlan.getInterest() .add(receivablePlan.getPrincipal()));
        // 应收时间
        receivablePlan.setShouldReceivableDate(repaymentPlan.getShouldRepaymentDate());
        // 应收状态, 当前业务为未收
        receivablePlan.setReceivableStatus(0);
        // 创建时间
        receivablePlan.setCreateDate(LocalDateTime.now());
        // 设置投资人让利, 注意这个地方是具体: 佣金
        receivablePlan.setCommission(commissionMap .get(repaymentPlan.getNumberOfPeriods()));

        // 保存到数据库
        receivablePlanMapper.insert(receivablePlan);
    }
}
