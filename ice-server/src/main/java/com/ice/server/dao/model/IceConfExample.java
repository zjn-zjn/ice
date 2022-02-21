package com.ice.server.dao.model;

import java.util.*;

public class IceConfExample {
    protected String orderByClause;

    protected boolean distinct;

    protected List<Criteria> oredCriteria;

    public IceConfExample() {
        oredCriteria = new ArrayList<Criteria>();
    }

    public void setOrderByClause(String orderByClause) {
        this.orderByClause = orderByClause;
    }

    public String getOrderByClause() {
        return orderByClause;
    }

    public void setDistinct(boolean distinct) {
        this.distinct = distinct;
    }

    public boolean isDistinct() {
        return distinct;
    }

    public List<Criteria> getOredCriteria() {
        return oredCriteria;
    }

    public void or(Criteria criteria) {
        oredCriteria.add(criteria);
    }

    public Criteria or() {
        Criteria criteria = createCriteriaInternal();
        oredCriteria.add(criteria);
        return criteria;
    }

    public Criteria createCriteria() {
        Criteria criteria = createCriteriaInternal();
        if (oredCriteria.size() == 0) {
            oredCriteria.add(criteria);
        }
        return criteria;
    }

    protected Criteria createCriteriaInternal() {
        Criteria criteria = new Criteria();
        return criteria;
    }

    public void clear() {
        oredCriteria.clear();
        orderByClause = null;
        distinct = false;
    }

    protected abstract static class GeneratedCriteria {
        protected List<Criterion> criteria;

        protected GeneratedCriteria() {
            super();
            criteria = new ArrayList<Criterion>();
        }

        public boolean isValid() {
            return criteria.size() > 0;
        }

        public List<Criterion> getAllCriteria() {
            return criteria;
        }

        public List<Criterion> getCriteria() {
            return criteria;
        }

        protected void addCriterion(String condition) {
            if (condition == null) {
                throw new RuntimeException("Value for condition cannot be null");
            }
            criteria.add(new Criterion(condition));
        }

        protected void addCriterion(String condition, Object value, String property) {
            if (value == null) {
                throw new RuntimeException("Value for " + property + " cannot be null");
            }
            criteria.add(new Criterion(condition, value));
        }

        protected void addCriterion(String condition, Object value1, Object value2, String property) {
            if (value1 == null || value2 == null) {
                throw new RuntimeException("Between values for " + property + " cannot be null");
            }
            criteria.add(new Criterion(condition, value1, value2));
        }

        public Criteria andIdIsNull() {
            addCriterion("id is null");
            return (Criteria) this;
        }

        public Criteria andIdIsNotNull() {
            addCriterion("id is not null");
            return (Criteria) this;
        }

        public Criteria andIdEqualTo(Long value) {
            addCriterion("id =", value, "id");
            return (Criteria) this;
        }

        public Criteria andIdNotEqualTo(Long value) {
            addCriterion("id <>", value, "id");
            return (Criteria) this;
        }

        public Criteria andIdGreaterThan(Long value) {
            addCriterion("id >", value, "id");
            return (Criteria) this;
        }

        public Criteria andIdGreaterThanOrEqualTo(Long value) {
            addCriterion("id >=", value, "id");
            return (Criteria) this;
        }

        public Criteria andIdLessThan(Long value) {
            addCriterion("id <", value, "id");
            return (Criteria) this;
        }

        public Criteria andIdLessThanOrEqualTo(Long value) {
            addCriterion("id <=", value, "id");
            return (Criteria) this;
        }

        public Criteria andIdIn(List<Long> values) {
            addCriterion("id in", values, "id");
            return (Criteria) this;
        }

        public Criteria andIdIn(Set<Long> values) {
            addCriterion("id in", values, "id");
            return (Criteria) this;
        }

        public Criteria andIdNotIn(List<Long> values) {
            addCriterion("id not in", values, "id");
            return (Criteria) this;
        }

        public Criteria andIdBetween(Long value1, Long value2) {
            addCriterion("id between", value1, value2, "id");
            return (Criteria) this;
        }

        public Criteria andIdNotBetween(Long value1, Long value2) {
            addCriterion("id not between", value1, value2, "id");
            return (Criteria) this;
        }

        public Criteria andAppIsNull() {
            addCriterion("app is null");
            return (Criteria) this;
        }

        public Criteria andAppIsNotNull() {
            addCriterion("app is not null");
            return (Criteria) this;
        }

        public Criteria andAppEqualTo(Integer value) {
            addCriterion("app =", value, "app");
            return (Criteria) this;
        }

        public Criteria andAppNotEqualTo(Integer value) {
            addCriterion("app <>", value, "app");
            return (Criteria) this;
        }

        public Criteria andAppGreaterThan(Integer value) {
            addCriterion("app >", value, "app");
            return (Criteria) this;
        }

        public Criteria andAppGreaterThanOrEqualTo(Integer value) {
            addCriterion("app >=", value, "app");
            return (Criteria) this;
        }

        public Criteria andAppLessThan(Integer value) {
            addCriterion("app <", value, "app");
            return (Criteria) this;
        }

        public Criteria andAppLessThanOrEqualTo(Integer value) {
            addCriterion("app <=", value, "app");
            return (Criteria) this;
        }

        public Criteria andAppIn(List<Integer> values) {
            addCriterion("app in", values, "app");
            return (Criteria) this;
        }

        public Criteria andAppNotIn(List<Integer> values) {
            addCriterion("app not in", values, "app");
            return (Criteria) this;
        }

        public Criteria andAppBetween(Integer value1, Integer value2) {
            addCriterion("app between", value1, value2, "app");
            return (Criteria) this;
        }

        public Criteria andAppNotBetween(Integer value1, Integer value2) {
            addCriterion("app not between", value1, value2, "app");
            return (Criteria) this;
        }

        public Criteria andNameIsNull() {
            addCriterion("name is null");
            return (Criteria) this;
        }

        public Criteria andNameIsNotNull() {
            addCriterion("name is not null");
            return (Criteria) this;
        }

        public Criteria andNameEqualTo(String value) {
            addCriterion("name =", value, "name");
            return (Criteria) this;
        }

        public Criteria andNameNotEqualTo(String value) {
            addCriterion("name <>", value, "name");
            return (Criteria) this;
        }

        public Criteria andNameGreaterThan(String value) {
            addCriterion("name >", value, "name");
            return (Criteria) this;
        }

        public Criteria andNameGreaterThanOrEqualTo(String value) {
            addCriterion("name >=", value, "name");
            return (Criteria) this;
        }

        public Criteria andNameLessThan(String value) {
            addCriterion("name <", value, "name");
            return (Criteria) this;
        }

        public Criteria andNameLessThanOrEqualTo(String value) {
            addCriterion("name <=", value, "name");
            return (Criteria) this;
        }

        public Criteria andNameLike(String value) {
            addCriterion("name like", value, "name");
            return (Criteria) this;
        }

        public Criteria andNameNotLike(String value) {
            addCriterion("name not like", value, "name");
            return (Criteria) this;
        }

        public Criteria andNameIn(List<String> values) {
            addCriterion("name in", values, "name");
            return (Criteria) this;
        }

        public Criteria andNameNotIn(List<String> values) {
            addCriterion("name not in", values, "name");
            return (Criteria) this;
        }

        public Criteria andNameBetween(String value1, String value2) {
            addCriterion("name between", value1, value2, "name");
            return (Criteria) this;
        }

        public Criteria andNameNotBetween(String value1, String value2) {
            addCriterion("name not between", value1, value2, "name");
            return (Criteria) this;
        }

        public Criteria andSonIdsIsNull() {
            addCriterion("son_ids is null");
            return (Criteria) this;
        }

        public Criteria andSonIdsIsNotNull() {
            addCriterion("son_ids is not null");
            return (Criteria) this;
        }

        public Criteria andSonIdsEqualTo(String value) {
            addCriterion("son_ids =", value, "sonIds");
            return (Criteria) this;
        }

        public Criteria andSonIdsNotEqualTo(String value) {
            addCriterion("son_ids <>", value, "sonIds");
            return (Criteria) this;
        }

        public Criteria andSonIdsGreaterThan(String value) {
            addCriterion("son_ids >", value, "sonIds");
            return (Criteria) this;
        }

        public Criteria andSonIdsGreaterThanOrEqualTo(String value) {
            addCriterion("son_ids >=", value, "sonIds");
            return (Criteria) this;
        }

        public Criteria andSonIdsLessThan(String value) {
            addCriterion("son_ids <", value, "sonIds");
            return (Criteria) this;
        }

        public Criteria andSonIdsLessThanOrEqualTo(String value) {
            addCriterion("son_ids <=", value, "sonIds");
            return (Criteria) this;
        }

        public Criteria andSonIdsLike(String value) {
            addCriterion("son_ids like", value, "sonIds");
            return (Criteria) this;
        }

        public Criteria andSonIdsNotLike(String value) {
            addCriterion("son_ids not like", value, "sonIds");
            return (Criteria) this;
        }

        public Criteria andSonIdsIn(List<String> values) {
            addCriterion("son_ids in", values, "sonIds");
            return (Criteria) this;
        }

        public Criteria andSonIdsNotIn(List<String> values) {
            addCriterion("son_ids not in", values, "sonIds");
            return (Criteria) this;
        }

        public Criteria andSonIdsBetween(String value1, String value2) {
            addCriterion("son_ids between", value1, value2, "sonIds");
            return (Criteria) this;
        }

        public Criteria andSonIdsNotBetween(String value1, String value2) {
            addCriterion("son_ids not between", value1, value2, "sonIds");
            return (Criteria) this;
        }

        public Criteria andTypeIsNull() {
            addCriterion("type is null");
            return (Criteria) this;
        }

        public Criteria andTypeIsNotNull() {
            addCriterion("type is not null");
            return (Criteria) this;
        }

        public Criteria andTypeEqualTo(Byte value) {
            addCriterion("type =", value, "type");
            return (Criteria) this;
        }

        public Criteria andTypeNotEqualTo(Byte value) {
            addCriterion("type <>", value, "type");
            return (Criteria) this;
        }

        public Criteria andTypeGreaterThan(Byte value) {
            addCriterion("type >", value, "type");
            return (Criteria) this;
        }

        public Criteria andTypeGreaterThanOrEqualTo(Byte value) {
            addCriterion("type >=", value, "type");
            return (Criteria) this;
        }

        public Criteria andTypeLessThan(Byte value) {
            addCriterion("type <", value, "type");
            return (Criteria) this;
        }

        public Criteria andTypeLessThanOrEqualTo(Byte value) {
            addCriterion("type <=", value, "type");
            return (Criteria) this;
        }

        public Criteria andTypeIn(List<Byte> values) {
            addCriterion("type in", values, "type");
            return (Criteria) this;
        }

        public Criteria andTypeNotIn(List<Byte> values) {
            addCriterion("type not in", values, "type");
            return (Criteria) this;
        }

        public Criteria andTypeBetween(Byte value1, Byte value2) {
            addCriterion("type between", value1, value2, "type");
            return (Criteria) this;
        }

        public Criteria andTypeNotBetween(Byte value1, Byte value2) {
            addCriterion("type not between", value1, value2, "type");
            return (Criteria) this;
        }

        public Criteria andStatusIsNull() {
            addCriterion("status is null");
            return (Criteria) this;
        }

        public Criteria andStatusIsNotNull() {
            addCriterion("status is not null");
            return (Criteria) this;
        }

        public Criteria andStatusEqualTo(Byte value) {
            addCriterion("status =", value, "status");
            return (Criteria) this;
        }

        public Criteria andStatusNotEqualTo(Byte value) {
            addCriterion("status <>", value, "status");
            return (Criteria) this;
        }

        public Criteria andStatusGreaterThan(Byte value) {
            addCriterion("status >", value, "status");
            return (Criteria) this;
        }

        public Criteria andStatusGreaterThanOrEqualTo(Byte value) {
            addCriterion("status >=", value, "status");
            return (Criteria) this;
        }

        public Criteria andStatusLessThan(Byte value) {
            addCriterion("status <", value, "status");
            return (Criteria) this;
        }

        public Criteria andStatusLessThanOrEqualTo(Byte value) {
            addCriterion("status <=", value, "status");
            return (Criteria) this;
        }

        public Criteria andStatusIn(List<Byte> values) {
            addCriterion("status in", values, "status");
            return (Criteria) this;
        }

        public Criteria andStatusNotIn(List<Byte> values) {
            addCriterion("status not in", values, "status");
            return (Criteria) this;
        }

        public Criteria andStatusBetween(Byte value1, Byte value2) {
            addCriterion("status between", value1, value2, "status");
            return (Criteria) this;
        }

        public Criteria andStatusNotBetween(Byte value1, Byte value2) {
            addCriterion("status not between", value1, value2, "status");
            return (Criteria) this;
        }

        public Criteria andInverseIsNull() {
            addCriterion("inverse is null");
            return (Criteria) this;
        }

        public Criteria andInverseIsNotNull() {
            addCriterion("inverse is not null");
            return (Criteria) this;
        }

        public Criteria andInverseEqualTo(Byte value) {
            addCriterion("inverse =", value, "inverse");
            return (Criteria) this;
        }

        public Criteria andInverseNotEqualTo(Byte value) {
            addCriterion("inverse <>", value, "inverse");
            return (Criteria) this;
        }

        public Criteria andInverseGreaterThan(Byte value) {
            addCriterion("inverse >", value, "inverse");
            return (Criteria) this;
        }

        public Criteria andInverseGreaterThanOrEqualTo(Byte value) {
            addCriterion("inverse >=", value, "inverse");
            return (Criteria) this;
        }

        public Criteria andInverseLessThan(Byte value) {
            addCriterion("inverse <", value, "inverse");
            return (Criteria) this;
        }

        public Criteria andInverseLessThanOrEqualTo(Byte value) {
            addCriterion("inverse <=", value, "inverse");
            return (Criteria) this;
        }

        public Criteria andInverseIn(List<Byte> values) {
            addCriterion("inverse in", values, "inverse");
            return (Criteria) this;
        }

        public Criteria andInverseNotIn(List<Byte> values) {
            addCriterion("inverse not in", values, "inverse");
            return (Criteria) this;
        }

        public Criteria andInverseBetween(Byte value1, Byte value2) {
            addCriterion("inverse between", value1, value2, "inverse");
            return (Criteria) this;
        }

        public Criteria andInverseNotBetween(Byte value1, Byte value2) {
            addCriterion("inverse not between", value1, value2, "inverse");
            return (Criteria) this;
        }

        public Criteria andConfNameIsNull() {
            addCriterion("conf_name is null");
            return (Criteria) this;
        }

        public Criteria andConfNameIsNotNull() {
            addCriterion("conf_name is not null");
            return (Criteria) this;
        }

        public Criteria andConfNameEqualTo(String value) {
            addCriterion("conf_name =", value, "confName");
            return (Criteria) this;
        }

        public Criteria andConfNameNotEqualTo(String value) {
            addCriterion("conf_name <>", value, "confName");
            return (Criteria) this;
        }

        public Criteria andConfNameGreaterThan(String value) {
            addCriterion("conf_name >", value, "confName");
            return (Criteria) this;
        }

        public Criteria andConfNameGreaterThanOrEqualTo(String value) {
            addCriterion("conf_name >=", value, "confName");
            return (Criteria) this;
        }

        public Criteria andConfNameLessThan(String value) {
            addCriterion("conf_name <", value, "confName");
            return (Criteria) this;
        }

        public Criteria andConfNameLessThanOrEqualTo(String value) {
            addCriterion("conf_name <=", value, "confName");
            return (Criteria) this;
        }

        public Criteria andConfNameLike(String value) {
            addCriterion("conf_name like", value, "confName");
            return (Criteria) this;
        }

        public Criteria andConfNameNotLike(String value) {
            addCriterion("conf_name not like", value, "confName");
            return (Criteria) this;
        }

        public Criteria andConfNameIn(List<String> values) {
            addCriterion("conf_name in", values, "confName");
            return (Criteria) this;
        }

        public Criteria andConfNameNotIn(List<String> values) {
            addCriterion("conf_name not in", values, "confName");
            return (Criteria) this;
        }

        public Criteria andConfNameBetween(String value1, String value2) {
            addCriterion("conf_name between", value1, value2, "confName");
            return (Criteria) this;
        }

        public Criteria andConfNameNotBetween(String value1, String value2) {
            addCriterion("conf_name not between", value1, value2, "confName");
            return (Criteria) this;
        }

        public Criteria andConfFieldIsNull() {
            addCriterion("conf_field is null");
            return (Criteria) this;
        }

        public Criteria andConfFieldIsNotNull() {
            addCriterion("conf_field is not null");
            return (Criteria) this;
        }

        public Criteria andConfFieldEqualTo(String value) {
            addCriterion("conf_field =", value, "confField");
            return (Criteria) this;
        }

        public Criteria andConfFieldNotEqualTo(String value) {
            addCriterion("conf_field <>", value, "confField");
            return (Criteria) this;
        }

        public Criteria andConfFieldGreaterThan(String value) {
            addCriterion("conf_field >", value, "confField");
            return (Criteria) this;
        }

        public Criteria andConfFieldGreaterThanOrEqualTo(String value) {
            addCriterion("conf_field >=", value, "confField");
            return (Criteria) this;
        }

        public Criteria andConfFieldLessThan(String value) {
            addCriterion("conf_field <", value, "confField");
            return (Criteria) this;
        }

        public Criteria andConfFieldLessThanOrEqualTo(String value) {
            addCriterion("conf_field <=", value, "confField");
            return (Criteria) this;
        }

        public Criteria andConfFieldLike(String value) {
            addCriterion("conf_field like", value, "confField");
            return (Criteria) this;
        }

        public Criteria andConfFieldNotLike(String value) {
            addCriterion("conf_field not like", value, "confField");
            return (Criteria) this;
        }

        public Criteria andConfFieldIn(List<String> values) {
            addCriterion("conf_field in", values, "confField");
            return (Criteria) this;
        }

        public Criteria andConfFieldNotIn(List<String> values) {
            addCriterion("conf_field not in", values, "confField");
            return (Criteria) this;
        }

        public Criteria andConfFieldBetween(String value1, String value2) {
            addCriterion("conf_field between", value1, value2, "confField");
            return (Criteria) this;
        }

        public Criteria andConfFieldNotBetween(String value1, String value2) {
            addCriterion("conf_field not between", value1, value2, "confField");
            return (Criteria) this;
        }

        public Criteria andIceIdIsNull() {
            addCriterion("ice_id is null");
            return (Criteria) this;
        }

        public Criteria andIceIdIsNotNull() {
            addCriterion("ice_id is not null");
            return (Criteria) this;
        }

        public Criteria andIceIdEqualTo(Long value) {
            addCriterion("ice_id =", value, "iceId");
            return (Criteria) this;
        }

        public Criteria andIceIdNotEqualTo(Long value) {
            addCriterion("ice_id <>", value, "iceId");
            return (Criteria) this;
        }

        public Criteria andIceIdGreaterThan(Long value) {
            addCriterion("ice_id >", value, "iceId");
            return (Criteria) this;
        }

        public Criteria andIceIdGreaterThanOrEqualTo(Long value) {
            addCriterion("ice_id >=", value, "iceId");
            return (Criteria) this;
        }

        public Criteria andIceIdLessThan(Long value) {
            addCriterion("ice_id <", value, "iceId");
            return (Criteria) this;
        }

        public Criteria andIceIdLessThanOrEqualTo(Long value) {
            addCriterion("ice_id <=", value, "iceId");
            return (Criteria) this;
        }

        public Criteria andIceIdIn(List<Long> values) {
            addCriterion("ice_id in", values, "iceId");
            return (Criteria) this;
        }

        public Criteria andIceIdNotIn(List<Long> values) {
            addCriterion("ice_id not in", values, "iceId");
            return (Criteria) this;
        }

        public Criteria andIceIdBetween(Long value1, Long value2) {
            addCriterion("ice_id between", value1, value2, "iceId");
            return (Criteria) this;
        }

        public Criteria andIceIdNotBetween(Long value1, Long value2) {
            addCriterion("ice_id not between", value1, value2, "iceId");
            return (Criteria) this;
        }

        public Criteria andForwardIdIsNull() {
            addCriterion("forward_id is null");
            return (Criteria) this;
        }

        public Criteria andForwardIdIsNotNull() {
            addCriterion("forward_id is not null");
            return (Criteria) this;
        }

        public Criteria andForwardIdEqualTo(Long value) {
            addCriterion("forward_id =", value, "forwardId");
            return (Criteria) this;
        }

        public Criteria andForwardIdNotEqualTo(Long value) {
            addCriterion("forward_id <>", value, "forwardId");
            return (Criteria) this;
        }

        public Criteria andForwardIdGreaterThan(Long value) {
            addCriterion("forward_id >", value, "forwardId");
            return (Criteria) this;
        }

        public Criteria andForwardIdGreaterThanOrEqualTo(Long value) {
            addCriterion("forward_id >=", value, "forwardId");
            return (Criteria) this;
        }

        public Criteria andForwardIdLessThan(Long value) {
            addCriterion("forward_id <", value, "forwardId");
            return (Criteria) this;
        }

        public Criteria andForwardIdLessThanOrEqualTo(Long value) {
            addCriterion("forward_id <=", value, "forwardId");
            return (Criteria) this;
        }

        public Criteria andForwardIdIn(List<Long> values) {
            addCriterion("forward_id in", values, "forwardId");
            return (Criteria) this;
        }

        public Criteria andForwardIdNotIn(List<Long> values) {
            addCriterion("forward_id not in", values, "forwardId");
            return (Criteria) this;
        }

        public Criteria andForwardIdBetween(Long value1, Long value2) {
            addCriterion("forward_id between", value1, value2, "forwardId");
            return (Criteria) this;
        }

        public Criteria andForwardIdNotBetween(Long value1, Long value2) {
            addCriterion("forward_id not between", value1, value2, "forwardId");
            return (Criteria) this;
        }

        public Criteria andTimeTypeIsNull() {
            addCriterion("time_type is null");
            return (Criteria) this;
        }

        public Criteria andTimeTypeIsNotNull() {
            addCriterion("time_type is not null");
            return (Criteria) this;
        }

        public Criteria andTimeTypeEqualTo(Byte value) {
            addCriterion("time_type =", value, "timeType");
            return (Criteria) this;
        }

        public Criteria andTimeTypeNotEqualTo(Byte value) {
            addCriterion("time_type <>", value, "timeType");
            return (Criteria) this;
        }

        public Criteria andTimeTypeGreaterThan(Byte value) {
            addCriterion("time_type >", value, "timeType");
            return (Criteria) this;
        }

        public Criteria andTimeTypeGreaterThanOrEqualTo(Byte value) {
            addCriterion("time_type >=", value, "timeType");
            return (Criteria) this;
        }

        public Criteria andTimeTypeLessThan(Byte value) {
            addCriterion("time_type <", value, "timeType");
            return (Criteria) this;
        }

        public Criteria andTimeTypeLessThanOrEqualTo(Byte value) {
            addCriterion("time_type <=", value, "timeType");
            return (Criteria) this;
        }

        public Criteria andTimeTypeIn(List<Byte> values) {
            addCriterion("time_type in", values, "timeType");
            return (Criteria) this;
        }

        public Criteria andTimeTypeNotIn(List<Byte> values) {
            addCriterion("time_type not in", values, "timeType");
            return (Criteria) this;
        }

        public Criteria andTimeTypeBetween(Byte value1, Byte value2) {
            addCriterion("time_type between", value1, value2, "timeType");
            return (Criteria) this;
        }

        public Criteria andTimeTypeNotBetween(Byte value1, Byte value2) {
            addCriterion("time_type not between", value1, value2, "timeType");
            return (Criteria) this;
        }

        public Criteria andStartIsNull() {
            addCriterion("start is null");
            return (Criteria) this;
        }

        public Criteria andStartIsNotNull() {
            addCriterion("start is not null");
            return (Criteria) this;
        }

        public Criteria andStartEqualTo(Date value) {
            addCriterion("start =", value, "start");
            return (Criteria) this;
        }

        public Criteria andStartNotEqualTo(Date value) {
            addCriterion("start <>", value, "start");
            return (Criteria) this;
        }

        public Criteria andStartGreaterThan(Date value) {
            addCriterion("start >", value, "start");
            return (Criteria) this;
        }

        public Criteria andStartGreaterThanOrEqualTo(Date value) {
            addCriterion("start >=", value, "start");
            return (Criteria) this;
        }

        public Criteria andStartLessThan(Date value) {
            addCriterion("start <", value, "start");
            return (Criteria) this;
        }

        public Criteria andStartLessThanOrEqualTo(Date value) {
            addCriterion("start <=", value, "start");
            return (Criteria) this;
        }

        public Criteria andStartIn(List<Date> values) {
            addCriterion("start in", values, "start");
            return (Criteria) this;
        }

        public Criteria andStartNotIn(List<Date> values) {
            addCriterion("start not in", values, "start");
            return (Criteria) this;
        }

        public Criteria andStartBetween(Date value1, Date value2) {
            addCriterion("start between", value1, value2, "start");
            return (Criteria) this;
        }

        public Criteria andStartNotBetween(Date value1, Date value2) {
            addCriterion("start not between", value1, value2, "start");
            return (Criteria) this;
        }

        public Criteria andEndIsNull() {
            addCriterion("end is null");
            return (Criteria) this;
        }

        public Criteria andEndIsNotNull() {
            addCriterion("end is not null");
            return (Criteria) this;
        }

        public Criteria andEndEqualTo(Date value) {
            addCriterion("end =", value, "end");
            return (Criteria) this;
        }

        public Criteria andEndNotEqualTo(Date value) {
            addCriterion("end <>", value, "end");
            return (Criteria) this;
        }

        public Criteria andEndGreaterThan(Date value) {
            addCriterion("end >", value, "end");
            return (Criteria) this;
        }

        public Criteria andEndGreaterThanOrEqualTo(Date value) {
            addCriterion("end >=", value, "end");
            return (Criteria) this;
        }

        public Criteria andEndLessThan(Date value) {
            addCriterion("end <", value, "end");
            return (Criteria) this;
        }

        public Criteria andEndLessThanOrEqualTo(Date value) {
            addCriterion("end <=", value, "end");
            return (Criteria) this;
        }

        public Criteria andEndIn(List<Date> values) {
            addCriterion("end in", values, "end");
            return (Criteria) this;
        }

        public Criteria andEndNotIn(List<Date> values) {
            addCriterion("end not in", values, "end");
            return (Criteria) this;
        }

        public Criteria andEndBetween(Date value1, Date value2) {
            addCriterion("end between", value1, value2, "end");
            return (Criteria) this;
        }

        public Criteria andEndNotBetween(Date value1, Date value2) {
            addCriterion("end not between", value1, value2, "end");
            return (Criteria) this;
        }

        public Criteria andDebugIsNull() {
            addCriterion("debug is null");
            return (Criteria) this;
        }

        public Criteria andDebugIsNotNull() {
            addCriterion("debug is not null");
            return (Criteria) this;
        }

        public Criteria andDebugEqualTo(Byte value) {
            addCriterion("debug =", value, "debug");
            return (Criteria) this;
        }

        public Criteria andDebugNotEqualTo(Byte value) {
            addCriterion("debug <>", value, "debug");
            return (Criteria) this;
        }

        public Criteria andDebugGreaterThan(Byte value) {
            addCriterion("debug >", value, "debug");
            return (Criteria) this;
        }

        public Criteria andDebugGreaterThanOrEqualTo(Byte value) {
            addCriterion("debug >=", value, "debug");
            return (Criteria) this;
        }

        public Criteria andDebugLessThan(Byte value) {
            addCriterion("debug <", value, "debug");
            return (Criteria) this;
        }

        public Criteria andDebugLessThanOrEqualTo(Byte value) {
            addCriterion("debug <=", value, "debug");
            return (Criteria) this;
        }

        public Criteria andDebugIn(List<Byte> values) {
            addCriterion("debug in", values, "debug");
            return (Criteria) this;
        }

        public Criteria andDebugNotIn(List<Byte> values) {
            addCriterion("debug not in", values, "debug");
            return (Criteria) this;
        }

        public Criteria andDebugBetween(Byte value1, Byte value2) {
            addCriterion("debug between", value1, value2, "debug");
            return (Criteria) this;
        }

        public Criteria andDebugNotBetween(Byte value1, Byte value2) {
            addCriterion("debug not between", value1, value2, "debug");
            return (Criteria) this;
        }

        public Criteria andCreateAtIsNull() {
            addCriterion("create_at is null");
            return (Criteria) this;
        }

        public Criteria andCreateAtIsNotNull() {
            addCriterion("create_at is not null");
            return (Criteria) this;
        }

        public Criteria andCreateAtEqualTo(Date value) {
            addCriterion("create_at =", value, "createAt");
            return (Criteria) this;
        }

        public Criteria andCreateAtNotEqualTo(Date value) {
            addCriterion("create_at <>", value, "createAt");
            return (Criteria) this;
        }

        public Criteria andCreateAtGreaterThan(Date value) {
            addCriterion("create_at >", value, "createAt");
            return (Criteria) this;
        }

        public Criteria andCreateAtGreaterThanOrEqualTo(Date value) {
            addCriterion("create_at >=", value, "createAt");
            return (Criteria) this;
        }

        public Criteria andCreateAtLessThan(Date value) {
            addCriterion("create_at <", value, "createAt");
            return (Criteria) this;
        }

        public Criteria andCreateAtLessThanOrEqualTo(Date value) {
            addCriterion("create_at <=", value, "createAt");
            return (Criteria) this;
        }

        public Criteria andCreateAtIn(List<Date> values) {
            addCriterion("create_at in", values, "createAt");
            return (Criteria) this;
        }

        public Criteria andCreateAtNotIn(List<Date> values) {
            addCriterion("create_at not in", values, "createAt");
            return (Criteria) this;
        }

        public Criteria andCreateAtBetween(Date value1, Date value2) {
            addCriterion("create_at between", value1, value2, "createAt");
            return (Criteria) this;
        }

        public Criteria andCreateAtNotBetween(Date value1, Date value2) {
            addCriterion("create_at not between", value1, value2, "createAt");
            return (Criteria) this;
        }

        public Criteria andUpdateAtIsNull() {
            addCriterion("update_at is null");
            return (Criteria) this;
        }

        public Criteria andUpdateAtIsNotNull() {
            addCriterion("update_at is not null");
            return (Criteria) this;
        }

        public Criteria andUpdateAtEqualTo(Date value) {
            addCriterion("update_at =", value, "updateAt");
            return (Criteria) this;
        }

        public Criteria andUpdateAtNotEqualTo(Date value) {
            addCriterion("update_at <>", value, "updateAt");
            return (Criteria) this;
        }

        public Criteria andUpdateAtGreaterThan(Date value) {
            addCriterion("update_at >", value, "updateAt");
            return (Criteria) this;
        }

        public Criteria andUpdateAtGreaterThanOrEqualTo(Date value) {
            addCriterion("update_at >=", value, "updateAt");
            return (Criteria) this;
        }

        public Criteria andUpdateAtLessThan(Date value) {
            addCriterion("update_at <", value, "updateAt");
            return (Criteria) this;
        }

        public Criteria andUpdateAtLessThanOrEqualTo(Date value) {
            addCriterion("update_at <=", value, "updateAt");
            return (Criteria) this;
        }

        public Criteria andUpdateAtIn(List<Date> values) {
            addCriterion("update_at in", values, "updateAt");
            return (Criteria) this;
        }

        public Criteria andUpdateAtNotIn(List<Date> values) {
            addCriterion("update_at not in", values, "updateAt");
            return (Criteria) this;
        }

        public Criteria andUpdateAtBetween(Date value1, Date value2) {
            addCriterion("update_at between", value1, value2, "updateAt");
            return (Criteria) this;
        }

        public Criteria andUpdateAtNotBetween(Date value1, Date value2) {
            addCriterion("update_at not between", value1, value2, "updateAt");
            return (Criteria) this;
        }

        public Criteria andNameLikeInsensitive(String value) {
            addCriterion("upper(name) like", value.toUpperCase(), "name");
            return (Criteria) this;
        }

        public Criteria andSonIdsLikeInsensitive(String value) {
            addCriterion("upper(son_ids) like", value.toUpperCase(), "sonIds");
            return (Criteria) this;
        }

        public Criteria andConfNameLikeInsensitive(String value) {
            addCriterion("upper(conf_name) like", value.toUpperCase(), "confName");
            return (Criteria) this;
        }

        public Criteria andConfFieldLikeInsensitive(String value) {
            addCriterion("upper(conf_field) like", value.toUpperCase(), "confField");
            return (Criteria) this;
        }
    }

    public static class Criteria extends GeneratedCriteria {

        protected Criteria() {
            super();
        }
    }

    public static class Criterion {
        private String condition;

        private Object value;

        private Object secondValue;

        private boolean noValue;

        private boolean singleValue;

        private boolean betweenValue;

        private boolean listValue;

        private String typeHandler;

        public String getCondition() {
            return condition;
        }

        public Object getValue() {
            return value;
        }

        public Object getSecondValue() {
            return secondValue;
        }

        public boolean isNoValue() {
            return noValue;
        }

        public boolean isSingleValue() {
            return singleValue;
        }

        public boolean isBetweenValue() {
            return betweenValue;
        }

        public boolean isListValue() {
            return listValue;
        }

        public String getTypeHandler() {
            return typeHandler;
        }

        protected Criterion(String condition) {
            super();
            this.condition = condition;
            this.typeHandler = null;
            this.noValue = true;
        }

        protected Criterion(String condition, Object value, String typeHandler) {
            super();
            this.condition = condition;
            this.value = value;
            this.typeHandler = typeHandler;
            if (value instanceof Collection<?>) {
                this.listValue = true;
            } else {
                this.singleValue = true;
            }
        }

        protected Criterion(String condition, Object value) {
            this(condition, value, null);
        }

        protected Criterion(String condition, Object value, Object secondValue, String typeHandler) {
            super();
            this.condition = condition;
            this.value = value;
            this.secondValue = secondValue;
            this.typeHandler = typeHandler;
            this.betweenValue = true;
        }

        protected Criterion(String condition, Object value, Object secondValue) {
            this(condition, value, secondValue, null);
        }
    }
}