package com.linearizability.templategenerator.area.model;

import lombok.Data;

import java.util.Objects;

/**
 * @author ZhangBoyuan
 * @since 2026-02-06
 */
@Data
public class SysArea {

    /**
     * 主键ID（编码）
     */
    private Long areaId;

    /**
     * 名称
     */
    private String areaName;

    /**
     * 父级ID
     */
    private Long areaPid;

    /**
     * 层级（2=省，3=市，4=区）
     */
    private Integer areaLevel;

    /**
     * 排序
     */
    private Integer sortIndex;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SysArea sysArea = (SysArea) o;
        return Objects.equals(areaName, sysArea.areaName) && Objects.equals(areaLevel, sysArea.areaLevel);
    }

    @Override
    public int hashCode() {
        return Objects.hash(areaName, areaLevel);
    }

}
