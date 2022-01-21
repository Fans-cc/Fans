package cn.itcast.wanxinp2p.search.service;

import cn.itcast.wanxinp2p.api.search.model.ProjectQueryParamsDTO;
import cn.itcast.wanxinp2p.api.transaction.model.ProjectDTO;
import cn.itcast.wanxinp2p.common.domain.PageVO;
import org.apache.commons.lang.StringUtils;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.sort.SortOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class ProjectIndexServiceImpl implements ProjectIndexService{
    @Autowired
    private RestHighLevelClient restHighLevelClient;
    @Value("${wanxinp2p.es.index}")
    private String projectIndex;

    @Override
    public PageVO<ProjectDTO> queryProjectIndex(ProjectQueryParamsDTO projectQueryParamsDTO, Integer pageNo, Integer pageSize, String sortBy, String order) {
        //1.创建搜索请求对象
        SearchRequest searchRequest = new SearchRequest(projectIndex);
        //2.搜索条件
        //2.1.创建条件查询对象
        BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery();
        //2.2.非空判断并封装条件
        //2.2.1 标的名称分词匹配
        if (StringUtils.isNotBlank(projectQueryParamsDTO.getName())){
            boolQueryBuilder.must(QueryBuilders.termQuery("name",projectQueryParamsDTO.getName()));
        }
        //2.2.2 起止标的期限(单位:天)
        if (projectQueryParamsDTO.getStartPeriod() != null){
            boolQueryBuilder.must(QueryBuilders.rangeQuery("period").gte(projectQueryParamsDTO.getStartPeriod()));
        }
        //2.2.3 终止标的期限(单位:天)
        if (projectQueryParamsDTO.getEndPeriod() != null){
            boolQueryBuilder.must(QueryBuilders.rangeQuery("period").lte(projectQueryParamsDTO.getEndPeriod()));
        }

        //3.创建searchSourceBuilder对象
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        //3.1.封装条件查询对象
        searchSourceBuilder.query(boolQueryBuilder);
        //3.2.设置排序信息
        if (StringUtils.isNotBlank(sortBy) && StringUtils.isNotBlank(order)){
            if (order.toLowerCase().equals("asc")){
                searchSourceBuilder.sort(sortBy, SortOrder.ASC);
            }
            if (order.toLowerCase().equals("desc")){
                searchSourceBuilder.sort(sortBy, SortOrder.DESC);
            }
        }else {
            searchSourceBuilder.sort("createdate", SortOrder.DESC);
        }
        //3.3.设置分页信息
        searchSourceBuilder.from((pageNo - 1)*pageSize);
        searchSourceBuilder.size(pageSize);
        //4.完成封装
        searchRequest.source(searchSourceBuilder);
        List<ProjectDTO> list = new ArrayList<>();
        PageVO<ProjectDTO> pageVO = new PageVO<>();
        //5.执行搜索
        try {
            SearchResponse search = restHighLevelClient.search(searchRequest, RequestOptions.DEFAULT);
            //6.获取响应结果
            SearchHits hits = search.getHits();
            long totalHits = hits.getTotalHits().value;
            pageVO.setTotal(totalHits);
            SearchHit[] hit = hits.getHits();
            //7.循环封装DTO
            for (SearchHit searchHit: hit
                 ) {
                ProjectDTO projectDTO = new ProjectDTO();
                Map<String, Object> map = searchHit.getSourceAsMap();
                Double amount = Double.valueOf(map.get("amount").toString()) ;
                String projectStatus = (String) map.get("projectstatus");
                Integer period = Integer.parseInt(map.get("period").toString());
                String name = (String) map.get("name");
                String description = (String) map.get("description");
                Long id = Long.parseLong(map.get("id").toString());
                String userNo = (String) map.get("userno");

                projectDTO.setId(id);
                projectDTO.setUserNo(userNo);
                projectDTO.setAmount(new BigDecimal(amount));
                projectDTO.setProjectStatus(projectStatus);
                projectDTO.setPeriod(period);
                projectDTO.setName(name);
                projectDTO.setDescription(description);

                list.add(projectDTO);
            }
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }


        //8.封装为PageVO对象并返回
        pageVO.setContent(list);
        pageVO.setPageNo(pageNo);
        pageVO.setPageSize(pageSize);

        return pageVO;
    }
}
