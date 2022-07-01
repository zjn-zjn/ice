//package com.ice.test.config;
//
//import com.ice.client.config.IceSpringBeanFactory;
//import com.ice.core.builder.IceBuilder;
//import com.ice.core.builder.LeafBuilder;
//import com.ice.core.builder.RelationBuilder;
//import com.ice.core.utils.IceBeanUtils;
//import com.ice.test.flow.ScoreFlow;
//import com.ice.test.result.AmountResult;
//import com.ice.test.result.PointResult;
//import org.springframework.context.annotation.Bean;
//import org.springframework.context.annotation.Configuration;
//
//import javax.annotation.PostConstruct;
//
///**
// * @author waitmoon
// */
//@Configuration
//public class TestConfig {
//
//  @Bean
//  public IceBeanUtils.IceBeanFactory iceBeanFactory() {
//    return new IceSpringBeanFactory();
//  }
//
//  @PostConstruct
//  public void iceBuild() {
//    iceBeanFactory();
//    AmountResult amountResult = new AmountResult();
//    amountResult.setKey("uid");
//    amountResult.setValue(5.0);
//    IceBeanUtils.autowireBean(amountResult);
//    PointResult pointResult = new PointResult();
//    pointResult.setKey("uid");
//    pointResult.setValue(10.0);
//    IceBeanUtils.autowireBean(pointResult);
//    ScoreFlow s100 = new ScoreFlow();
//    s100.setKey("spend");
//    s100.setScore(100.0);
//    ScoreFlow s50 = new ScoreFlow();
//    s100.setKey("spend");
//    s100.setScore(50.0);
//
//    RelationBuilder amountAnd = RelationBuilder.andRelation().son(LeafBuilder.leaf(s100), LeafBuilder.leaf(amountResult));
//    RelationBuilder pointAnd = RelationBuilder.andRelation().son(LeafBuilder.leaf(s50), LeafBuilder.leaf(pointResult));
//    RelationBuilder rootAny = RelationBuilder.anyRelation().son(amountAnd, pointAnd);
//    IceBuilder.root(rootAny).debug((byte) 7).register("recharge-1");
//  }
//}
