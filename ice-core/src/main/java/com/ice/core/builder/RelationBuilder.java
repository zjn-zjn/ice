package com.ice.core.builder;

import com.ice.common.enums.TimeTypeEnum;
import com.ice.core.base.BaseNode;
import com.ice.core.base.BaseRelation;
import com.ice.core.relation.All;
import com.ice.core.relation.And;
import com.ice.core.relation.Any;
import com.ice.core.relation.None;
import com.ice.core.relation.True;

import java.util.Arrays;

/**
 * @author zjn
 */
public class RelationBuilder extends BaseBuilder {

  public RelationBuilder(BaseRelation relation) {
    super(relation);
  }

  public RelationBuilder(RelationBuilder builder) {
    super(builder.build());
  }

  public static RelationBuilder andRelation() {
    return new RelationBuilder(new And());
  }

  public static RelationBuilder anyRelation() {
    return new RelationBuilder(new Any());
  }

  public static RelationBuilder allRelation() {
    return new RelationBuilder(new All());
  }

  public static RelationBuilder noneRelation() {
    return new RelationBuilder(new None());
  }

  public static RelationBuilder trueRelation() {
    return new RelationBuilder(new True());
  }

  @Override
  public RelationBuilder forward(BaseNode forward) {
    return (RelationBuilder) super.forward(forward);
  }

  @Override
  public RelationBuilder forward(BaseBuilder builder) {
    return (RelationBuilder) super.forward(builder);
  }

  @Override
  public RelationBuilder start(long start) {
    return (RelationBuilder) super.start(start);
  }

  @Override
  public RelationBuilder end(long end) {
    return (RelationBuilder) super.end(end);
  }

  @Override
  public RelationBuilder timeType(TimeTypeEnum typeEnum) {
    return (RelationBuilder) super.timeType(typeEnum);
  }

  public RelationBuilder son(BaseNode... nodes) {
    ((BaseRelation) this.getNode()).getChildren().addAll(nodes);
    return this;
  }

  public RelationBuilder son(BaseBuilder... builders) {
    BaseNode[] nodes = Arrays.stream(builders).map(BaseBuilder::build).toArray(BaseNode[]::new);
    ((BaseRelation) this.getNode()).getChildren().addAll(nodes);
    return this;
  }
}