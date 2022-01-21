package cn.itcast.wanxinp2p.transaction.service;

import cn.itcast.wanxinp2p.api.consumer.model.BalanceDetailsDTO;
import cn.itcast.wanxinp2p.api.consumer.model.ConsumerDTO;
import cn.itcast.wanxinp2p.api.depository.model.LoanDetailRequest;
import cn.itcast.wanxinp2p.api.depository.model.LoanRequest;
import cn.itcast.wanxinp2p.api.depository.model.UserAutoPreTransactionRequest;
import cn.itcast.wanxinp2p.api.repayment.model.ProjectWithTendersDTO;
import cn.itcast.wanxinp2p.api.repayment.model.TenderOverviewDTO;
import cn.itcast.wanxinp2p.api.transaction.model.*;
import cn.itcast.wanxinp2p.common.domain.*;
import cn.itcast.wanxinp2p.common.util.CodeNoUtil;
import cn.itcast.wanxinp2p.common.util.StringUtil;
import cn.itcast.wanxinp2p.transaction.agent.ConsumerApiAgent;
import cn.itcast.wanxinp2p.transaction.agent.ContentSearchApiAgent;
import cn.itcast.wanxinp2p.transaction.agent.DepositoryAgentApiAgent;
import cn.itcast.wanxinp2p.transaction.common.constant.TradingCode;
import cn.itcast.wanxinp2p.transaction.common.constant.TransactionErrorCode;
import cn.itcast.wanxinp2p.transaction.common.utils.IncomeCalcUtil;
import cn.itcast.wanxinp2p.transaction.common.utils.SecurityUtil;
import cn.itcast.wanxinp2p.transaction.entity.Project;
import cn.itcast.wanxinp2p.transaction.entity.Tender;
import cn.itcast.wanxinp2p.transaction.mapper.ProjectMapper;
import cn.itcast.wanxinp2p.transaction.mapper.TenderMapper;
import cn.itcast.wanxinp2p.transaction.message.P2pTransactionProducer;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Slf4j
@Service
public class ProjectServiceImpl extends ServiceImpl<ProjectMapper, Project> implements ProjectService {
    @Autowired
    ConsumerApiAgent consumerApiAgent;
    @Autowired
    DepositoryAgentApiAgent depositoryAgentApiAgent;
    @Autowired
    ContentSearchApiAgent contentSearchApiAgent;
    @Autowired
    ConfigService configService;
    @Autowired
    TenderMapper tenderMapper;
    @Autowired
    private P2pTransactionProducer p2pTransactionProducer;
    @Override
    public ProjectDTO createProject(ProjectDTO projectDTO) {
        //获取当前登陆用户信息
        final RestResponse<ConsumerDTO> currConsumer = consumerApiAgent.getCurrConsumer(SecurityUtil.getUser().getMobile());
        //设置用户编码
        projectDTO.setUserNo(currConsumer.getResult().getUserNo());
        //设置用户id
        projectDTO.setConsumerId(currConsumer.getResult().getId());
        //生成标的编码
        projectDTO.setProjectNo(CodeNoUtil.getNo(CodePrefixCode.CODE_PROJECT_PREFIX));
        //标的状态修改
        projectDTO.setProjectStatus(ProjectCode.COLLECTING.getCode());
        //标的可用状态修改, 未同步
        projectDTO.setStatus(StatusCode.STATUS_OUT.getCode());
        //设置标的创建时间
        projectDTO.setCreateDate(LocalDateTime.now());
        //设置还款方式
        projectDTO.setRepaymentWay(RepaymentWayCode.FIXED_REPAYMENT.getCode());
        //设置标的类型
        projectDTO.setType("new");
        //把dto转换为entity
        Project project = convertProjectDTOToEntity(projectDTO);
        // 设置利率(需要在Apollo上进行配置)
        // 年化利率(借款人视图)
        project.setBorrowerAnnualRate(configService.getBorrowerAnnualRate());
        // 年化利率(投资人视图)
        project.setAnnualRate(configService.getAnnualRate());
        // 年化利率(平台佣金，利差)
        project.setCommissionAnnualRate(configService.getCommissionAnnualRate());
        // 债权转让
        project.setIsAssignment(0);
        // 设置标的名字, 姓名+性别+第N次借款
        // 判断男女
        String sex = Integer.parseInt(currConsumer.getResult().getIdNumber()
                .substring(16,17)) % 2 == 0 ? "女士" : "男士";

        project.setName(currConsumer.getResult().getFullname()+sex
        +"第"+count(Wrappers.<Project>lambdaQuery().eq(Project::getConsumerId,currConsumer.getResult().getId()))+1+"次借款");
        save(project);
        projectDTO.setId(project.getId());
        projectDTO.setName(project.getName());
        return projectDTO;
    }

    @Override
    public PageVO<ProjectDTO> queryProjectsByQueryDTO(ProjectQueryDTO projectQueryDTO, String order, Integer pageNo, Integer pageSize, String sortBy) {
        //条件构造器
        QueryWrapper<Project> queryWrapper = new QueryWrapper<>();
        //标的类型
        if (StringUtils.isNotBlank(projectQueryDTO.getType())){
            queryWrapper.lambda().eq(Project::getType,projectQueryDTO.getType());
        }
        //起止年化利率(投资人) -- 区间
        if (null != projectQueryDTO.getStartAnnualRate()){
            queryWrapper.lambda().ge(Project::getAnnualRate,projectQueryDTO.getStartAnnualRate());
        }
        if (null != projectQueryDTO.getEndAnnualRate()){
            queryWrapper.lambda().le(Project::getAnnualRate,projectQueryDTO.getEndAnnualRate());
        }
        // 借款期限 -- 区间
        if (null != projectQueryDTO.getStartPeriod()){
            queryWrapper.lambda().ge(Project::getPeriod,projectQueryDTO.getStartPeriod());
        }
        if (null != projectQueryDTO.getEndPeriod()){
            queryWrapper.lambda().ge(Project::getPeriod,projectQueryDTO.getEndPeriod());
        }
        // 标的状态
        if (StringUtils.isNotBlank(projectQueryDTO.getProjectStatus())){
            queryWrapper.lambda().eq(Project::getProjectStatus,projectQueryDTO.getProjectStatus());
        }

        // 处理排序 order值: desc 或者 asc sortBy: 排序字段
        if (StringUtils.isNotBlank(order) && StringUtils.isNotBlank(sortBy)){
            if (order.toLowerCase().equals("desc")){
                queryWrapper.orderByDesc(sortBy);
            }else if (order.toLowerCase().equals("asc")){
                queryWrapper.orderByAsc(sortBy);
            }
        }else {
            queryWrapper.lambda().orderByDesc(Project::getCreateDate);
        }
        // 构造分页对象
        IPage<Project> iPage = new Page<>(pageNo,pageSize);
        IPage<Project> projectIPage = page(iPage, queryWrapper);
        // ENTITY转换为DTO, 不向外部暴露ENTITY
        List<ProjectDTO> dtoList = convertProjectEntityListToDTOList(projectIPage.getRecords());
        return new PageVO<>(dtoList,projectIPage.getTotal(),pageNo,pageSize);
    }

    @Override
    public String projectsApprovalStatus(Long id, String approveStatus) {
        // 1.根据id查询出标的信息并转换为DTO对象
        Project project = getById(id);
        ProjectDTO projectDTO = convertProjectEntityToDTO(project);
        // 2.生成请求流水号(不存在才生成)
        if (StringUtil.isBlank(project.getRequestNo())){
            projectDTO.setRequestNo(CodeNoUtil.getNo(CodePrefixCode.CODE_REQUEST_PREFIX));
            update(Wrappers.<Project>lambdaUpdate().set(Project::getRequestNo,projectDTO.getRequestNo()).eq(Project::getId,id));
        }

        // 3.调用存管代理服务同步标的信息
        RestResponse<String> restResponse = depositoryAgentApiAgent.createProject(projectDTO);
        if (DepositoryReturnCode.RETURN_CODE_00000.getCode().equals(restResponse.getResult())){
            // 4.修改状态为: 已发布
            update(Wrappers.<Project>lambdaUpdate().set(Project::getStatus,Integer.parseInt(approveStatus)).eq(Project::getId,id));
            return "success";
        }else {
            // 5.失败抛出一个业务异常
            throw new BusinessException(TransactionErrorCode.E_150113);
        }

    }

    @Override
    public PageVO<ProjectDTO> queryProjects(ProjectQueryDTO projectQueryDTO,
                                            String order, Integer pageNo, Integer pageSize, String sortBy) {
        RestResponse<PageVO<ProjectDTO>> esResponse = contentSearchApiAgent.queryProjectIndex(projectQueryDTO, pageNo, pageSize, sortBy, order);
        if (!esResponse.isSuccessful()){
            throw new BusinessException(CommonErrorCode.E_999998);
        }
        return esResponse.getResult();
    }

    @Override
    public List<ProjectDTO> queryProjectsIds(String ids) {
        //QueryWrapper<Project> queryWrapper = new QueryWrapper();
        List<Long> list = new ArrayList<>();
        Arrays.asList(ids.split(",")).forEach(str ->{
            list.add(Long.parseLong(str));
        } );

        //queryWrapper.lambda().in(Project::getId, list);
        List<Project> projects = list(Wrappers.<Project>lambdaQuery().in(Project::getId, list));
        List<ProjectDTO> projectDTOS = new ArrayList<>();
        for (Project project: projects
             ) {
            //转化为dto对象
            ProjectDTO projectDTO = convertProjectEntityToDTO(project);
            // 封装剩余额度
            projectDTO.setRemainingAmount(getProjectRemainingAmount(project));
            // 封装标的已投记录数
            projectDTO.setTenderCount(tenderMapper.selectCount(Wrappers.<Tender>lambdaQuery().eq(Tender::getProjectId,project.getId())));
            projectDTOS.add(projectDTO);
        }
        return projectDTOS;
    }

    @Override
    public List<TenderOverviewDTO> queryTendersByProjectId(Long id) {
        List<Tender> tenderList = tenderMapper.selectList(Wrappers.<Tender>lambdaQuery().eq(Tender::getProjectId, id));
        return convertTenderEntityListToDTOList(tenderList);
    }

    /**
     * 1）接受用户填写的投标信息
     * 2）交易中心校验投资金额是否符合平台允许最小投资金额
     * 3）校验用户余额是否大于投资金额
     * 4）校验投资金额是否小于等于标的可投金额
     * 5）校验此次投标后的剩余金额是否满足最小投资金额
     * 6）保存投标信息
     * 7）请求存管代理服务进行投标预处理冻结
     * 8）存管代理服务返回处理结果给交易中心，交易中心计算此次投标预期收益
     * 9）返回预期收益给前端
     * @param projectInvestDTO
     * @return
     */
    @Override
    public TenderDTO createTender(ProjectInvestDTO projectInvestDTO) {
        //1.交易中心校验投资金额是否符合平台允许最小投资金额
        //1.1 读取Apollo上的最小投标金额配置
        BigDecimal miniInvestmentAmount = configService.getMiniInvestmentAmount();
        //1.2 比较当前投资金额和最小投资金额的大小
        BigDecimal amount = new BigDecimal(projectInvestDTO.getAmount());
        if (miniInvestmentAmount.compareTo(amount) > 0){
            //1.3当前投资金额大于最小投资金额
            throw new BusinessException(TransactionErrorCode.E_150109);
        }
        //2.校验用户余额是否大于投资金额
        //2.1 获取当前用户
        ConsumerDTO consumerDTO = consumerApiAgent.getCurrConsumer(SecurityUtil.getUser().getMobile()).getResult();
        //2.2 获取当前用户余额
        BalanceDetailsDTO balanceDetailsDTO = consumerApiAgent.getBalance(consumerDTO.getUserNo()).getResult();
        BigDecimal balance = balanceDetailsDTO.getBalance();
        //2.3 判断当前用户余额是否充足
        if (balance.compareTo(amount) < 0){
            throw new BusinessException(TransactionErrorCode.E_150112);
        }
        //3.校验投资金额是否小于等于标的可投金额
        //3.1 判断是否满标
        Project project = getById(projectInvestDTO.getId());
        if (project.getProjectStatus().equalsIgnoreCase("FULLY")){
            throw new BusinessException(TransactionErrorCode.E_150114);
        }
        //3.2 获取标的的未投金额
        BigDecimal remainingAmount = getProjectRemainingAmount(project);
        //3.3 比较当前投标金额和未投金额
        if(amount.compareTo(remainingAmount) > 0){
            throw new BusinessException(TransactionErrorCode.E_150110);
        }else {
            //4.校验此次投标后的剩余金额是否满足最小投资金额
            BigDecimal subtract = remainingAmount.subtract(amount);
            if (subtract.compareTo(new BigDecimal("100")) < 0){
                throw new BusinessException(TransactionErrorCode.E_150111);
            }
        }

        //5. 保存投标信息 数据状态为: 未发布
        // 封装投标信息
        final Tender tender = new Tender();
        // 投资人投标金额( 投标冻结金额 )
        tender.setAmount(amount);
        // 投标人用户信息
        tender.setConsumerId(consumerDTO.getId());
        tender.setConsumerUsername(consumerDTO.getUsername());
        tender.setUserNo(consumerDTO.getUserNo());
        // 标的标识
        tender.setProjectId(projectInvestDTO.getId());
        // 标的编码
        tender.setProjectNo(project.getProjectNo());
        // 标的名称
        tender.setProjectName(project.getName());
        // 标的期限(单位:天)
        tender.setProjectPeriod(project.getPeriod());
        // 年化利率(投资人视图)
        tender.setProjectAnnualRate(project.getAnnualRate());
        // 投标状态
        tender.setTenderStatus(TradingCode.FROZEN.getCode());
        // 创建时间
        tender.setCreateDate(LocalDateTime.now());
        // 请求流水号
        tender.setRequestNo(CodeNoUtil.getNo(CodePrefixCode.CODE_REQUEST_PREFIX));
        // 可用状态
        tender.setStatus(0);
        // 保存到数据库
        tenderMapper.insert(tender);


        // 6.发送数据给存管代理服务
        // 构造请求数据
        UserAutoPreTransactionRequest userAutoPreTransactionRequest = new UserAutoPreTransactionRequest();
        //冻结金额
        userAutoPreTransactionRequest.setAmount(amount);
        // 预处理业务类型
        userAutoPreTransactionRequest.setBizType(PreprocessBusinessTypeCode.TENDER.getCode());
        // 标的号
        userAutoPreTransactionRequest.setProjectNo(project.getProjectNo());
        // 请求流水号
        userAutoPreTransactionRequest.setRequestNo(tender.getRequestNo());
        // 投资人用户编码
        userAutoPreTransactionRequest.setUserNo(consumerDTO.getUserNo());
        // 设置 关联业务实体标识
        userAutoPreTransactionRequest.setId(tender.getId());
        // 远程调用存管代理服务
        RestResponse<String> response = depositoryAgentApiAgent.userAutoPreTransaction(userAutoPreTransactionRequest);

        //7. 判断返回的结果
        if (response.getResult().equals(DepositoryReturnCode.RETURN_CODE_00000.getCode())){
            //7.1 修改投标状态为已发布
            tender.setStatus(1);
            tenderMapper.updateById(tender);
            //7.2 投标成功后判断标的是否已投满, 如果满标, 更新标的状态
            remainingAmount = getProjectRemainingAmount(project);
            if (remainingAmount.compareTo(new BigDecimal("0.0")) == 0){
                project.setProjectStatus(ProjectCode.FULLY.getCode());
                updateById(project);
            }

            // 7.3 转换为dto对象并封装数据
            TenderDTO tenderDTO = convertTenderEntityToDTO(tender);
            // 封装标的信息
            project.setRepaymentWay(RepaymentWayCode.FIXED_REPAYMENT.getCode());
            tenderDTO.setProject(convertProjectEntityToDTO(project));
            // 封装预期收益
            // 根据标的期限计算还款月数
            final Double ceil = Math.ceil(project.getPeriod() / 30.0);
            Integer month = ceil.intValue();
            //计算预期收益
            BigDecimal totalInterest = IncomeCalcUtil.getIncomeTotalInterest(new BigDecimal(projectInvestDTO.getAmount()), configService.getAnnualRate(), month);
            tenderDTO.setExpectedIncome(totalInterest);
            return tenderDTO;
        }else {
            // 抛出一个业务异常
            log.warn("投标失败 ! 标的ID为: {" + projectInvestDTO.getId().toString()  +"}, 存管代理服务返回的状态为: {" + response.getResult() + "}"  );

            throw new BusinessException(TransactionErrorCode.E_150113);
        }

    }

    /**
     * 审核标的满标放款
     * 第一阶段: 生成还款明细
     * 第二阶段: 放款
     * 第三阶段: 修改标的的业务状态
     * @param id
     * @param approveStatus
     * @param commission
     * @return
     */
    @Override
    public String loansApprovalStatus(Long id, String approveStatus, String commission) {
        //1. 生产还款明细
        //1.1 根据标的id查询标的信息
        Project project = getById(id);
        //1.2 获取投标信息
        List<Tender> tenders = tenderMapper.selectList(Wrappers.<Tender>lambdaQuery().eq(Tender::getProjectId, id));
        //1.3 生产还款明细
        LoanRequest loanRequest = generateLoanRequest(project, tenders, commission);
        //2. 放款
        //2.1 请求存管代理服务
        RestResponse<String> restResponse = depositoryAgentApiAgent.confirmLoan(loanRequest);
        if (restResponse.getResult().equals(DepositoryReturnCode.RETURN_CODE_00000.getCode())){
            // 返回成功的消息,更新投标信息: 已放款
            updateTenderStatusAlreadyLoan(tenders);
            // 3. 修改标的业务状态
            // 调用存管代理服务，修改状态为还款中
            ModifyProjectStatusDTO modifyProjectStatusDTO = new ModifyProjectStatusDTO();
            // 设置业务实体id
            modifyProjectStatusDTO.setId(project.getId());
            // 设置业务状态
            modifyProjectStatusDTO.setProjectStatus(ProjectCode.REPAYING.getCode());
            // 设置请求流水号
            modifyProjectStatusDTO.setRequestNo(loanRequest.getRequestNo());
            // 调用存管代理服务 执行请求
            RestResponse<String> modifyProjectStatus = depositoryAgentApiAgent.modifyProjectStatus(modifyProjectStatusDTO);
            if (modifyProjectStatus.getResult().equals(DepositoryReturnCode.RETURN_CODE_00000.getCode())){
                //如果处理成功，就修改标的状态为还款中
                project.setProjectStatus(ProjectCode.REPAYING.getCode());
                updateById(project);
                // 4. 启动还款
                // 封装调用还款服务请求对象的数据
                ProjectWithTendersDTO projectWithTendersDTO = new ProjectWithTendersDTO();
                // 4.1 封装标的信息
                projectWithTendersDTO.setProject(convertProjectEntityToDTO(project));
                // 4.2 封装投标信息
                projectWithTendersDTO.setTenders(convertTenderEntityListToDTOList2(tenders));
                // 4.3 封装投资人让利
                projectWithTendersDTO.setCommissionBorrowerAnnualRate(configService.getCommissionBorrowerAnnualRate());
                // 4.4 调用还款服务, 启动还款(生成还款计划、应收明细)
                p2pTransactionProducer.updateProjectStatusAndStartRepayment(project,projectWithTendersDTO);

                return "审核成功";

            }else{
                // 失败抛出一个业务异常
                log.warn("审核满标放款失败 ! 标的ID为: {}, 存管代理服务返回的状态为: {}", project.getId(), restResponse.getResult());
                throw new BusinessException(TransactionErrorCode.E_150113);
            }

        }else {
            log.warn("审核满标放款失败 ! 标的ID为: {}, 存管代理服务返回的状态为: {}", project.getId(), restResponse.getResult());
            throw new BusinessException(TransactionErrorCode.E_150113);
        }

    }

    /**
     * 执行本地事务(修
     * 改标的状态为还款中)
     * @param project
     * @return
     */
    @Transactional(rollbackFor = BusinessException.class)
    @Override
    public Boolean updateProjectStatusAndStartRepayment(Project project) {
        project.setProjectStatus(ProjectCode.REPAYING.getCode());
        return updateById(project);
    }

    /**
     * 根据标的及投标信息生成放款明细
     * @param project
     * @param tenderList
     * @param commission
     * @return
     */
    public LoanRequest generateLoanRequest (Project project , List<Tender> tenderList , String commission){
        //1. 创建放款明细对象
        LoanRequest loanRequest = new LoanRequest();
        //1.1 设置标的id
        loanRequest.setId(project.getId());
        //1.2 设置请求流水号
        loanRequest.setProjectNo(CodeNoUtil.getNo(CodePrefixCode.CODE_REQUEST_PREFIX));
        //1.3 设置标的编码
        loanRequest.setRequestNo(project.getRequestNo());
        //1.4 设置平台佣金
        if (StringUtils.isNotBlank(commission)){
            loanRequest.setCommission(new BigDecimal(commission));
        }
        //1.5 处理放款明细
        List<LoanDetailRequest> details = new ArrayList<>();

        tenderList.forEach(tender -> {
            final LoanDetailRequest loanDetailRequest = new LoanDetailRequest();
            //1.5.1 设置放款金额
            loanDetailRequest.setAmount(tender.getAmount());
            //1.5.2 设置预处理业务流水号
            loanDetailRequest.setPreRequestNo(tender.getRequestNo());
            details.add(loanDetailRequest);
        });
        //1.6 设置放款明细
        loanRequest.setDetails(details);
        //1.7 返回封装好的明细
        return loanRequest;
    }

    public void updateTenderStatusAlreadyLoan(List<Tender> tenderList){
        tenderList.forEach(tender -> {
            // 设置状态为已放款
            tender.setTenderStatus(TradingCode.LOAN.getCode());
            tenderMapper.updateById(tender);
        });
    }

    private Project convertProjectDTOToEntity(ProjectDTO projectDTO) {
        if (projectDTO == null) {
            return null;
        }
        Project project = new Project();
        BeanUtils.copyProperties(projectDTO, project);
        return project;
    }

    private ProjectDTO convertProjectEntityToDTO(Project project) {
        if (project == null) {
            return null;
        }
        ProjectDTO projectDTO = new ProjectDTO();
        BeanUtils.copyProperties(project, projectDTO);
        return projectDTO;
    }

    private List<ProjectDTO>convertProjectEntityListToDTOList(List<Project> projectList) {
        if (projectList == null) {
            return null;
        }
        List<ProjectDTO> dtoList = new ArrayList<>();
        projectList.forEach(project -> {
            ProjectDTO projectDTO = new ProjectDTO();
            BeanUtils.copyProperties(project,projectDTO);
            dtoList.add(projectDTO);
        });

        return dtoList;
    }

    private List<TenderOverviewDTO> convertTenderEntityListToDTOList(List<Tender> tenderList){
        if (tenderList == null){
            return null;
        }
        List<TenderOverviewDTO> tenderOverviewDTOS = new ArrayList<>();
        TenderOverviewDTO tenderOverviewDTO = new TenderOverviewDTO();
        for (Tender tender:tenderList
             ) {
            BeanUtils.copyProperties(tender,tenderOverviewDTO);
            tenderOverviewDTOS.add(tenderOverviewDTO);
            tenderOverviewDTO = null;
        }
        return tenderOverviewDTOS;
    }

    private BigDecimal getProjectRemainingAmount(Project project){
        //查询当前标的的已投金额总和
        List<BigDecimal> bigDecimals = tenderMapper.selectAmountInvestedByProjectId(project.getId());
        //封装结果集
        BigDecimal amountInvested = new BigDecimal("0.0");
        for (BigDecimal b: bigDecimals
             ) {
            amountInvested.add(b);
        }
        return project.getAmount().subtract(amountInvested);
    }

    private TenderDTO convertTenderEntityToDTO(Tender tender) {
        if (tender == null) {
            return null;
        }TenderDTO tenderDTO = new TenderDTO();
        BeanUtils.copyProperties(tender, tenderDTO);
        return tenderDTO;
    }



    private List<TenderDTO> convertTenderEntityListToDTOList2(List<Tender> tenderList){
        if (tenderList == null){
            return null;
        }
        List<TenderDTO> tenderDTOS = new ArrayList<>();
        TenderDTO tenderDTO = new TenderDTO();
        for (Tender tender:tenderList
        ) {
            BeanUtils.copyProperties(tender,tenderDTO);
            tenderDTOS.add(tenderDTO);
            tenderDTO = null;
        }
        return tenderDTOS;
    }
}
