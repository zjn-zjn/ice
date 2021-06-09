package com.ice.test.flow;

import com.ice.core.annotation.IceParam;
import com.ice.test.service.SendService;
import lombok.Data;
import org.springframework.util.CollectionUtils;

import javax.annotation.Resource;
import java.util.Set;

/**
 * @author zjn
 * 取出roam中的值比较大小
 */
@Data
public final class SetFlow {

  private Set<Integer> set;

  @Resource
  private SendService sendService;

  private static boolean compare(@IceParam("one") Integer one, @IceParam("two") Integer two) {
    return one > two;
  }

/*
   * 叶子节点流程处理
   */
  public boolean contains(@IceParam("uid") Integer uid) {
    if (CollectionUtils.isEmpty(set)) {
      return false;
    }
    sendService.sendPoint(uid, 10);
    return set.contains(uid);
  }
}
