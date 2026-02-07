package com.linearizability.templategenerator.area;

import com.linearizability.templategenerator.area.model.SysArea;
import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.DataValidation;
import org.apache.poi.ss.usermodel.DataValidationConstraint;
import org.apache.poi.ss.util.CellRangeAddressList;
import org.apache.poi.xssf.usermodel.*;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 行政区域下拉模板生成
 * 生成包含省市区三级级联下拉的Excel模板
 * 用户看到的是中文名称，程序可读取area_id
 * <p>
 * 实现方案：
 * 1. 在隐藏工作表中维护ID和名称的映射关系（A列存储ID，B列存储名称）
 * 2. 主工作表的A、B、C列：用户选择省、市、区（显示中文名称）
 * 3. 主工作表的D、E、F列：隐藏列，通过公式自动提取并存储area_id
 * 4. 数据验证下拉列表只显示名称，用户选择后：
 * - A、B、C列存储用户选择的名称
 * - D、E、F列通过公式自动查找对应的ID并存储
 * 5. 程序读取时，从D、E、F列读取area_id值
 *
 * @author ZhangBoyuan
 * @since 2026-02-06
 */
@Slf4j
public class AreaTemplateGenerator {

    /**
     * Properties实例，用于加载配置
     */
    private static final Properties props = loadProperties(AreaTemplateGenerator.class.getClassLoader());

    /**
     * 数据库连接信息
     *
     */
    private static final String DB_URL = props.getProperty("db.url");

    /**
     * 数据库用户名
     */
    private static final String DB_USERNAME = props.getProperty("db.username");

    /**
     * 数据库密码
     */
    private static final String DB_PASSWORD = props.getProperty("db.password");

    /**
     * 数据库驱动
     */
    private static final String DB_DRIVER = props.getProperty("db.driver");

    /**
     * 隐藏工作表名称
     */
    private static final String HIDDEN_SHEET_NAME = "AreaData";

    /**
     * 主工作表名称
     */
    private static final String MAIN_SHEET_NAME = "省市区选择";

    private static final String OUTPUT_PATH = "D://area_template.xlsx";

    // 区域层级常量
    private static final int PROVINCE_LEVEL = 2;
    private static final int CITY_LEVEL = 3;
    private static final int DISTRICT_LEVEL = 4;

    // 列索引常量
    private static final int PROVINCE_NAME_COL = 0;  // A列：省
    private static final int CITY_NAME_COL = 1;      // B列：市
    private static final int DISTRICT_NAME_COL = 2;  // C列：区
    private static final int PROVINCE_ID_COL = 3;    // D列：省ID（隐藏）
    private static final int CITY_ID_COL = 4;        // E列：市ID（隐藏）
    private static final int DISTRICT_ID_COL = 5;    // F列：区ID（隐藏）

    // 数据验证相关常量
    private static final int DATA_ROWS_COUNT = 10000;  // 数据验证行数
    private static final int HEADER_ROW_INDEX = 0;     // 表头行索引
    private static final int DATA_START_ROW_INDEX = 1; // 数据起始行索引

    // 样式相关常量
    private static final int COLUMN_WIDTH = 5000;      // 列宽度
    private static final byte[] HEADER_BG_COLOR = {(byte) 217, (byte) 217, (byte) 217}; // 表头背景色

    // SQL查询常量
    private static final String AREA_QUERY_SQL = String.format("""
            SELECT area_id, area_name, area_pid, area_level, sort_index
            FROM sys_area
            WHERE area_level IN (%d, %d, %d)
            ORDER BY area_level, sort_index;
            """, PROVINCE_LEVEL, CITY_LEVEL, DISTRICT_LEVEL);

    // 隐藏工作表列索引常量
    private static final int HIDDEN_PROVINCE_ID_COL = 0;        // 省份ID列
    private static final int HIDDEN_PROVINCE_NAME_COL = 1;      // 省份名称列
    private static final int HIDDEN_CITY_START_COL = 2;         // 城市数据起始列

    /**
     * 加载配置文件
     */
    private static Properties loadProperties(ClassLoader classLoader) {
        Properties properties = new Properties();
        try (InputStream input = classLoader.getResourceAsStream("db.properties")) {
            if (Objects.isNull(input)) {
                log.error("无法找到 db.properties 配置文件");
                throw new RuntimeException("无法找到 db.properties 配置文件");
            }
            properties.load(input);
        } catch (IOException e) {
            log.error("加载配置文件失败");
            throw new RuntimeException("加载配置文件失败", e);
        }
        return properties;
    }

    /**
     * 主方法，用于测试
     */
    static void main() {
        try {
            generateExcel(OUTPUT_PATH);
            log.info("生成完成！");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 生成Excel模板文件
     *
     * @param outputPath 输出文件路径
     * @throws IOException IO异常
     */
    public static void generateExcel(String outputPath) throws IOException {
        // 1. 连接数据库并查询数据
        List<SysArea> allAreas = queryAreaData();

        // 2. 按层级分类数据
        Map<Integer, List<SysArea>> areasByLevel = allAreas.stream()
                .collect(Collectors.groupingBy(SysArea::getAreaLevel));

        List<SysArea> provinces = areasByLevel.getOrDefault(PROVINCE_LEVEL, new ArrayList<>());
        List<SysArea> cities = areasByLevel.getOrDefault(CITY_LEVEL, new ArrayList<>());
        List<SysArea> districts = areasByLevel.getOrDefault(DISTRICT_LEVEL, new ArrayList<>());

        // 按sort_index排序
        provinces.sort(Comparator.comparing(SysArea::getSortIndex));
        cities.sort(Comparator.comparing(SysArea::getSortIndex));
        districts.sort(Comparator.comparing(SysArea::getSortIndex));

        // 3. 创建Excel工作簿
        XSSFWorkbook workbook = new XSSFWorkbook();

        // 4. 创建隐藏工作表存储数据
        XSSFSheet hiddenSheet = workbook.createSheet(HIDDEN_SHEET_NAME);
        workbook.setSheetHidden(workbook.getSheetIndex(HIDDEN_SHEET_NAME), true);

        // 5. 在隐藏工作表中填充数据
        // 第一列：省份ID，第二列：省份名称（用于VLOOKUP查找显示名称）
        for (int i = 0; i < provinces.size(); i++) {
            SysArea province = provinces.get(i);
            XSSFRow row = hiddenSheet.createRow(i);
            row.createCell(HIDDEN_PROVINCE_ID_COL).setCellValue(province.getAreaId());
            row.createCell(HIDDEN_PROVINCE_NAME_COL).setCellValue(province.getAreaName());
        }

        // 为每个省创建对应的市数据（从第3列开始，每两列为一组：ID列和名称列）
        int cityStartCol = HIDDEN_CITY_START_COL;
        Map<Long, Integer> provinceToCityColMap = new HashMap<>(); // 省ID -> 市数据起始列（ID列）
        for (SysArea province : provinces) {
            List<SysArea> provinceCities = cities.stream()
                    .filter(city -> city.getAreaPid().equals(province.getAreaId()))
                    .sorted(Comparator.comparing(SysArea::getSortIndex))
                    .toList();

            if (!provinceCities.isEmpty()) {
                provinceToCityColMap.put(province.getAreaId(), cityStartCol);
                for (int i = 0; i < provinceCities.size(); i++) {
                    SysArea city = provinceCities.get(i);
                    XSSFRow row = hiddenSheet.getRow(i);
                    if (row == null) {
                        row = hiddenSheet.createRow(i);
                    }
                    // ID列
                    row.createCell(cityStartCol).setCellValue(city.getAreaId());
                    // 名称列（紧邻ID列，用于VLOOKUP查找显示名称）
                    row.createCell(cityStartCol + 1).setCellValue(city.getAreaName());
                }
                cityStartCol += 2;
            }
        }

        // 为每个市创建对应的区数据（继续使用两列一组的方式：ID列和名称列）
        int districtStartCol = cityStartCol;
        Map<Long, Integer> cityToDistrictColMap = new HashMap<>(); // 市ID -> 区数据起始列（ID列）
        for (SysArea city : cities) {
            List<SysArea> cityDistricts = districts.stream()
                    .filter(district -> district.getAreaPid().equals(city.getAreaId()))
                    .sorted(Comparator.comparing(SysArea::getSortIndex))
                    .toList();

            if (!cityDistricts.isEmpty()) {
                cityToDistrictColMap.put(city.getAreaId(), districtStartCol);
                for (int i = 0; i < cityDistricts.size(); i++) {
                    SysArea district = cityDistricts.get(i);
                    XSSFRow row = hiddenSheet.getRow(i);
                    if (row == null) {
                        row = hiddenSheet.createRow(i);
                    }
                    // ID列
                    row.createCell(districtStartCol).setCellValue(district.getAreaId());
                    // 名称列（紧邻ID列，用于VLOOKUP查找显示名称）
                    row.createCell(districtStartCol + 1).setCellValue(district.getAreaName());
                }
                districtStartCol += 2;
            }
        }

        // 6. 创建名称管理器（用于数据验证下拉列表）
        XSSFNameHelper nameHelper = new XSSFNameHelper(workbook);

        // 创建省份名称列表（只显示名称，不显示ID）
        int provinceNameCol = districtStartCol;
        for (int i = 0; i < provinces.size(); i++) {
            SysArea province = provinces.get(i);
            XSSFRow row = hiddenSheet.getRow(i);
            if (row == null) {
                row = hiddenSheet.createRow(i);
            }
            // 只存储名称，用于下拉显示
            row.createCell(provinceNameCol).setCellValue(province.getAreaName());
        }
        String provinceNameRange = String.format("'%s'!$%s$1:$%s$%d",
                HIDDEN_SHEET_NAME, getColumnName(provinceNameCol), getColumnName(provinceNameCol), provinces.size());
        nameHelper.createName("ProvinceNames", provinceNameRange);

        // 为每个省创建市名称列表（只显示名称，不显示ID）
        int cityNameStartCol = provinceNameCol + 1;
        int currentCityNameCol = cityNameStartCol;
        for (SysArea province : provinces) {
            List<SysArea> provinceCities = cities.stream()
                    .filter(city -> city.getAreaPid().equals(province.getAreaId()))
                    .sorted(Comparator.comparing(SysArea::getSortIndex))
                    .toList();

            if (!provinceCities.isEmpty()) {
                for (int i = 0; i < provinceCities.size(); i++) {
                    SysArea city = provinceCities.get(i);
                    XSSFRow row = hiddenSheet.getRow(i);
                    if (row == null) {
                        row = hiddenSheet.createRow(i);
                    }
                    // 只存储名称，用于下拉显示
                    row.createCell(currentCityNameCol).setCellValue(city.getAreaName());
                }
                String cityNameRange = String.format("'%s'!$%s$1:$%s$%d",
                        HIDDEN_SHEET_NAME, getColumnName(currentCityNameCol),
                        getColumnName(currentCityNameCol), provinceCities.size());
                nameHelper.createName("Province_" + province.getAreaId() + "_CityNames", cityNameRange);
                currentCityNameCol++;
            }
        }

        // 为每个市创建区名称列表（只显示名称，不显示ID）
        int districtNameStartCol = currentCityNameCol;
        int currentDistrictNameCol = districtNameStartCol;
        for (SysArea city : cities) {
            List<SysArea> cityDistricts = districts.stream()
                    .filter(district -> district.getAreaPid().equals(city.getAreaId()))
                    .sorted(Comparator.comparing(SysArea::getSortIndex))
                    .collect(Collectors.toList());

            if (!cityDistricts.isEmpty()) {
                for (int i = 0; i < cityDistricts.size(); i++) {
                    SysArea district = cityDistricts.get(i);
                    XSSFRow row = hiddenSheet.getRow(i);
                    if (row == null) {
                        row = hiddenSheet.createRow(i);
                    }
                    // 只存储名称，用于下拉显示
                    row.createCell(currentDistrictNameCol).setCellValue(district.getAreaName());
                }
                String districtNameRange = String.format("'%s'!$%s$1:$%s$%d",
                        HIDDEN_SHEET_NAME, getColumnName(currentDistrictNameCol),
                        getColumnName(currentDistrictNameCol), cityDistricts.size());
                nameHelper.createName("City_" + city.getAreaId() + "_DistrictNames", districtNameRange);
                currentDistrictNameCol++;
            }
        }

        // 为每个省创建市的ID范围和名称范围（用于VLOOKUP查找）
        for (SysArea province : provinces) {
            Integer cityCol = provinceToCityColMap.get(province.getAreaId());
            if (cityCol != null) {
                List<SysArea> provinceCities = cities.stream()
                        .filter(city -> city.getAreaPid().equals(province.getAreaId()))
                        .sorted(Comparator.comparing(SysArea::getSortIndex))
                        .collect(Collectors.toList());

                int cityCount = provinceCities.size();
                // ID列范围（用于VLOOKUP查找ID）
                String cityIdRange = String.format("'%s'!$%s$1:$%s$%d",
                        HIDDEN_SHEET_NAME,
                        getColumnName(cityCol),
                        getColumnName(cityCol),
                        cityCount);
                nameHelper.createName("Province_" + province.getAreaId() + "_Ids", cityIdRange);

                // 名称列范围（用于MATCH查找位置）
                String cityNameRangeForLookup = String.format("'%s'!$%s$1:$%s$%d",
                        HIDDEN_SHEET_NAME,
                        getColumnName(cityCol + 1),
                        getColumnName(cityCol + 1),
                        cityCount);
                nameHelper.createName("Province_" + province.getAreaId() + "_Names", cityNameRangeForLookup);
            }
        }

        // 为每个市创建区的ID范围和名称范围（用于VLOOKUP查找）
        for (SysArea city : cities) {
            Integer districtCol = cityToDistrictColMap.get(city.getAreaId());
            if (districtCol != null) {
                List<SysArea> cityDistricts = districts.stream()
                        .filter(district -> district.getAreaPid().equals(city.getAreaId()))
                        .sorted(Comparator.comparing(SysArea::getSortIndex))
                        .toList();

                int districtCount = cityDistricts.size();
                // ID列范围（用于VLOOKUP查找ID）
                String districtIdRange = String.format("'%s'!$%s$1:$%s$%d",
                        HIDDEN_SHEET_NAME,
                        getColumnName(districtCol),
                        getColumnName(districtCol),
                        districtCount);
                nameHelper.createName("City_" + city.getAreaId() + "_Ids", districtIdRange);

                // 名称列范围（用于MATCH查找位置）
                String districtNameRangeForLookup = String.format("'%s'!$%s$1:$%s$%d",
                        HIDDEN_SHEET_NAME,
                        getColumnName(districtCol + 1),
                        getColumnName(districtCol + 1),
                        districtCount);
                nameHelper.createName("City_" + city.getAreaId() + "_Names", districtNameRangeForLookup);
            }
        }

        // 7. 创建主工作表
        XSSFSheet mainSheet = workbook.createSheet(MAIN_SHEET_NAME);
        workbook.setActiveSheet(workbook.getSheetIndex(MAIN_SHEET_NAME));

        // 创建表头（只显示中文）
        XSSFRow headerRow = mainSheet.createRow(HEADER_ROW_INDEX);
        headerRow.createCell(PROVINCE_NAME_COL).setCellValue("省");
        headerRow.createCell(CITY_NAME_COL).setCellValue("市");
        headerRow.createCell(DISTRICT_NAME_COL).setCellValue("区");
        headerRow.createCell(PROVINCE_ID_COL).setCellValue("area_id_level1");
        headerRow.createCell(CITY_ID_COL).setCellValue("area_id_level2");
        headerRow.createCell(DISTRICT_ID_COL).setCellValue("area_id_level3");

        // 设置表头样式
        XSSFCellStyle headerStyle = workbook.createCellStyle();
        XSSFFont headerFont = workbook.createFont();
        headerFont.setBold(true);
        headerStyle.setFont(headerFont);
        headerStyle.setFillForegroundColor(new XSSFColor(HEADER_BG_COLOR, null));
        headerStyle.setFillPattern(org.apache.poi.ss.usermodel.FillPatternType.SOLID_FOREGROUND);
        for (int i = 0; i < 3; i++) {
            headerRow.getCell(i).setCellStyle(headerStyle);
        }

        // 8. 设置数据验证（级联下拉）
        // 方案：A、B、C列显示中文名称（用户选择），D、E、F列隐藏存储area_id
        // 下拉列表显示名称，用户选择后，通过公式将名称转换为ID存储到隐藏列
        // A列：省名称下拉（只显示名称，不显示ID）
        CellRangeAddressList provinceAddressList = new CellRangeAddressList(1, DATA_ROWS_COUNT, 0, 0);
        DataValidationConstraint provinceConstraint = mainSheet.getDataValidationHelper()
                .createFormulaListConstraint("ProvinceNames");
        DataValidation provinceValidation = mainSheet.getDataValidationHelper()
                .createValidation(provinceConstraint, provinceAddressList);
        provinceValidation.setShowErrorBox(true);
        mainSheet.addValidationData(provinceValidation);

        // D列：隐藏列，存储省ID（从A列的名称转换为ID）
        mainSheet.setColumnHidden(3, true);
        for (int i = 1; i <= DATA_ROWS_COUNT; i++) {
            XSSFRow row = mainSheet.getRow(i);
            if (row == null) {
                row = mainSheet.createRow(i);
            }
            // D列存储ID：根据A列的名称，在隐藏sheet中查找对应的ID
            String provinceIdFormula = String.format("IF($A%d=\"\",\"\",INDEX('%s'!$A$1:$A$%d,MATCH($A%d,'%s'!$B$1:$B$%d,0)))",
                    i + 1, HIDDEN_SHEET_NAME, provinces.size(), i + 1, HIDDEN_SHEET_NAME, provinces.size());
            row.createCell(3).setCellFormula(provinceIdFormula);
        }

        // B列：市名称下拉（级联，只显示名称）
        CellRangeAddressList cityAddressList = new CellRangeAddressList(1, DATA_ROWS_COUNT, 1, 1);
        // 从D列的省ID，查找对应的市列表
        String cityFormula = "IF($D2=\"\",\"\",INDIRECT(\"Province_\" & $D2 & \"_CityNames\"))";
        DataValidationConstraint cityConstraint = mainSheet.getDataValidationHelper()
                .createFormulaListConstraint(cityFormula);
        DataValidation cityValidation = mainSheet.getDataValidationHelper()
                .createValidation(cityConstraint, cityAddressList);
        cityValidation.setShowErrorBox(true);
        mainSheet.addValidationData(cityValidation);

        // E列：隐藏列，存储市ID（从B列的名称转换为ID）
        mainSheet.setColumnHidden(4, true);
        for (int i = 1; i <= DATA_ROWS_COUNT; i++) {
            XSSFRow row = mainSheet.getRow(i);
            if (row == null) {
                row = mainSheet.createRow(i);
            }
            // E列存储ID：根据B列的名称和D列的省ID，在隐藏sheet中查找对应的市ID
            String cityIdFormula = String.format("IF(OR($B%d=\"\",$D%d=\"\"),\"\",INDEX(INDIRECT(\"Province_\" & $D%d & \"_Ids\"),MATCH($B%d,INDIRECT(\"Province_\" & $D%d & \"_Names\"),0)))",
                    i + 1, i + 1, i + 1, i + 1, i + 1);
            row.createCell(4).setCellFormula(cityIdFormula);
        }

        // C列：区名称下拉（级联，只显示名称）
        CellRangeAddressList districtAddressList = new CellRangeAddressList(1, DATA_ROWS_COUNT, 2, 2);
        // 从E列的市ID，查找对应的区列表
        String districtFormula = "IF($E2=\"\",\"\",INDIRECT(\"City_\" & $E2 & \"_DistrictNames\"))";
        DataValidationConstraint districtConstraint = mainSheet.getDataValidationHelper()
                .createFormulaListConstraint(districtFormula);
        DataValidation districtValidation = mainSheet.getDataValidationHelper()
                .createValidation(districtConstraint, districtAddressList);
        districtValidation.setShowErrorBox(true);
        mainSheet.addValidationData(districtValidation);

        // F列：隐藏列，存储区ID（从C列的名称转换为ID）
        mainSheet.setColumnHidden(5, true);
        for (int i = 1; i <= DATA_ROWS_COUNT; i++) {
            XSSFRow row = mainSheet.getRow(i);
            if (row == null) {
                row = mainSheet.createRow(i);
            }
            // F列存储ID：根据C列的名称和E列的市ID，在隐藏sheet中查找对应的区ID
            String districtIdFormula = String.format("IF(OR($C%d=\"\",$E%d=\"\"),\"\",INDEX(INDIRECT(\"City_\" & $E%d & \"_Ids\"),MATCH($C%d,INDIRECT(\"City_\" & $E%d & \"_Names\"),0)))",
                    i + 1, i + 1, i + 1, i + 1, i + 1);
            row.createCell(5).setCellFormula(districtIdFormula);
        }

        // 9. 设置列宽
        mainSheet.setColumnWidth(PROVINCE_NAME_COL, COLUMN_WIDTH);  // 省（显示名称）
        mainSheet.setColumnWidth(CITY_NAME_COL, COLUMN_WIDTH);      // 市（显示名称）
        mainSheet.setColumnWidth(DISTRICT_NAME_COL, COLUMN_WIDTH);  // 区（显示名称）

        // 10. 冻结表头
        mainSheet.createFreezePane(0, DATA_START_ROW_INDEX + 1);

        // 11. 保存文件
        try (FileOutputStream fos = new FileOutputStream(outputPath)) {
            workbook.write(fos);
        }

        workbook.close();
        log.info("Excel模板生成成功: {}", outputPath);
        log.info("说明：");
        log.info("  - A、B、C列：用户选择省、市、区（显示中文名称，如\"澳门特别行政区\"）");
        log.info("  - D、E、F列：隐藏列，自动存储对应的area_id（程序读取时使用）");
        log.info("  - 用户在下拉列表中选择名称后，D、E、F列会自动提取并存储对应的area_id");
        log.info("  - 程序读取时，从D、E、F列读取area_id值");
    }

    /**
     * 查询sys_area表数据
     *
     * @return 区域数据列表
     */
    private static List<SysArea> queryAreaData() {
        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setJdbcUrl(DB_URL);
        dataSource.setUsername(DB_USERNAME);
        dataSource.setPassword(DB_PASSWORD);
        dataSource.setDriverClassName(DB_DRIVER);
        dataSource.setMaximumPoolSize(5);

        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

        List<SysArea> areas = jdbcTemplate.query(AREA_QUERY_SQL, new BeanPropertyRowMapper<>(SysArea.class));

        dataSource.close();
        return areas;
    }

    /**
     * 获取Excel列名（A, B, C, ..., Z, AA, AB, ...）
     *
     * @param colIndex 列索引（从0开始）
     * @return 列名
     */
    private static String getColumnName(int colIndex) {
        StringBuilder sb = new StringBuilder();
        colIndex++;
        while (colIndex > 0) {
            colIndex--;
            sb.insert(0, (char) ('A' + colIndex % 26));
            colIndex /= 26;
        }
        return sb.toString();
    }

    /**
     * 名称管理器辅助类
     */
    private static class XSSFNameHelper {
        private final XSSFWorkbook workbook;

        public XSSFNameHelper(XSSFWorkbook workbook) {
            this.workbook = workbook;
        }

        public void createName(String name, String formula) {
            XSSFName xssfName = workbook.createName();
            xssfName.setNameName(name);
            xssfName.setRefersToFormula(formula);
        }
    }
}
