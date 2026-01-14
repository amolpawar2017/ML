import ec.tstoolkit.timeseries.simplets.TsData;
import ec.tstoolkit.timeseries.simplets.TsPeriod;
import ec.tstoolkit.timeseries.simplets.TsFrequency;
import ec.tstoolkit.modelling.arima.tramo.TramoSpecification;
import ec.tstoolkit.modelling.arima.IPreprocessor;
import ec.tstoolkit.modelling.arima.PreprocessingModel;
import ec.satoolkit.algorithm.implementation.TramoSeatsProcessingFactory;
import ec.satoolkit.tramoseats.TramoSeatsSpecification;
import ec.tstoolkit.timeseries.regression.OutlierType;
import ec.tstoolkit.modelling.RegArimaEstimation;
import ec.tstoolkit.modelling.arima.tramo.TramoModelEstimator;
import ec.tstoolkit.timeseries.regression.IOutlierVariable;
import ec.tstoolkit.maths.linearfilters.BackFilter;

import java.io.FileInputStream;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;

public class OutlierDetectionExample {
    
    public static void main(String[] args) {
        try {
            // 1. Load data from Excel file
            TsData timeSeries = loadDataFromExcel("C:\\Amol\\Book1.xlsx", "Sheet1", "S1");
            
            // 2. Create TRAMO specification for outlier detection
            // Using TRfull which enables automatic outlier detection
            TramoSpecification spec = TramoSpecification.TRfull.clone();
            
            // 3. Configure outlier detection settings
            // Set critical value (0 means use default)
            spec.getOutliers().setCriticalValue(0.0);
            
            // Enable specific outlier types: AO, LS, TC
            spec.getOutliers().add(OutlierType.AO); // Additive Outlier
            spec.getOutliers().add(OutlierType.LS); // Level Shift
            spec.getOutliers().add(OutlierType.TC); // Transitory Change
            
            // You can disable SO (Seasonal Outlier) if not needed
            spec.getOutliers().remove(OutlierType.SO);
            
            // 4. Build the preprocessor
            IPreprocessor preprocessor = spec.build();
            
            // 5. Run the preprocessing/outlier detection
            PreprocessingModel model = preprocessor.process(timeSeries, null);
            
            // 6. Display results in formatted tables like the app
            displayOutlierResults(model);
            
        }
    
    /**
     * Display outlier results in formatted tables like the JDemetra+ app
     */
    private static void displayOutlierResults(PreprocessingModel model) {
        int nOutliers = model.description.getOutliers().size();
        
        System.out.println("\n╔════════════════════════════════════════════════════════╗");
        System.out.println("║                      Outliers                          ║");
        System.out.println("╚════════════════════════════════════════════════════════╝");
        System.out.println("\nNumber of outliers : " + nOutliers);
        
        if (nOutliers > 0) {
            // Get coefficients and standard errors
            double[] coefficients = model.estimation.getLikelihood().getB();
            double[][] bvar = model.estimation.getLikelihood().getBVar();
            int startPos = model.description.getRegressionVariablesStartingPosition();
            
            // Print detailed outlier table
            System.out.println("\n┌─────────┬──────────┬──────────────┬──────────────┬──────────────┐");
            System.out.println("│  Type   │  Period  │    Value     │    StdErr    │    TStat     │");
            System.out.println("├─────────┼──────────┼──────────────┼──────────────┼──────────────┤");
            
            // Store data for summary
            Map<String, Integer> outlierCounts = new HashMap<>();
            Map<String, Double> outlierSums = new HashMap<>();
            outlierCounts.put("AO", 0);
            outlierCounts.put("LS", 0);
            outlierCounts.put("TC", 0);
            outlierCounts.put("SO", 0);
            outlierSums.put("AO", 0.0);
            outlierSums.put("LS", 0.0);
            outlierSums.put("TC", 0.0);
            outlierSums.put("SO", 0.0);
            
            for (int i = 0; i < nOutliers; i++) {
                IOutlierVariable outlier = model.description.getOutliers().get(i);
                String type = outlier.getCode();
                TsPeriod period = outlier.getPosition();
                
                // Get coefficient and standard error
                double coef = coefficients[startPos + i];
                double stderr = Math.sqrt(bvar[startPos + i][startPos + i]);
                double tstat = coef / stderr;
                
                // Update summary data
                outlierCounts.put(type, outlierCounts.get(type) + 1);
                outlierSums.put(type, outlierSums.get(type) + coef);
                
                // Format period (e.g., "4-2020" for April 2020)
                String periodStr = String.format("%d-%d", 
                    period.getPosition() + 1, // Month (1-based)
                    period.getYear());
                
                // Print row with color indicator (simulated with type)
                System.out.printf("│ %-7s │ %-8s │ %12.4f │ %12.4f │ %12.4f │%n",
                    type, periodStr, coef, stderr, tstat);
            }
            
            System.out.println("└─────────┴──────────┴──────────────┴──────────────┴──────────────┘");
            
            // Print Summary table
            System.out.println("\n╔════════════════════════════════════════════════════════╗");
            System.out.println("║                      Summary                           ║");
            System.out.println("╚════════════════════════════════════════════════════════╝");
            System.out.println("\n┌─────────┬──────────┬──────────────┐");
            System.out.println("│  Type   │  Number  │  Avg Value   │");
            System.out.println("├─────────┼──────────┼──────────────┤");
            
            // Print summary for each outlier type
            String[] types = {"LS", "SO", "AO", "TC"};
            for (String type : types) {
                int count = outlierCounts.get(type);
                double avgValue = count > 0 ? outlierSums.get(type) / count : 0.0;
                System.out.printf("│ %-7s │ %8d │ %12.4f │%n", type, count, avgValue);
            }
            
            System.out.println("└─────────┴──────────┴──────────────┘");
            
            // Print additional model information
            System.out.println("\n╔════════════════════════════════════════════════════════╗");
            System.out.println("║                   Model Information                    ║");
            System.out.println("╚════════════════════════════════════════════════════════╝");
            System.out.println("\nARIMA Model: " + model.description.getArimaComponent());
            System.out.println("Log transformation: " + model.description.isLogTransformation());
            System.out.println("Standard Error: " + 
                String.format("%.4f", Math.sqrt(model.estimation.getLikelihood().getSsqErr() / 
                                                model.estimation.getLikelihood().getN())));
            System.out.println("AIC: " + String.format("%.4f", model.estimation.getStatistics().AIC));
            System.out.println("BIC: " + String.format("%.4f", model.estimation.getStatistics().BIC));
        } else {
            System.out.println("\nNo outliers detected.");
        }
    } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    /**
     * Load time series data from Excel file
     */
    private static TsData loadDataFromExcel(String filePath, String sheetName, 
                                           String columnName) throws Exception {
        FileInputStream fis = new FileInputStream(filePath);
        Workbook workbook = new XSSFWorkbook(fis);
        Sheet sheet = workbook.getSheet(sheetName);
        
        List<Double> values = new ArrayList<>();
        
        // Find column index
        Row headerRow = sheet.getRow(0);
        int colIndex = -1;
        for (Cell cell : headerRow) {
            if (cell.getStringCellValue().equals(columnName)) {
                colIndex = cell.getColumnIndex();
                break;
            }
        }
        
        if (colIndex == -1) {
            throw new Exception("Column " + columnName + " not found");
        }
        
        // Read date from first data row to determine start period
        Row firstDataRow = sheet.getRow(1);
        Cell dateCell = firstDataRow.getCell(0);
        
        // Assuming the date format is "1-2015" (Month-Year) as shown in screenshot
        String dateStr = dateCell.getStringCellValue();
        String[] parts = dateStr.split("-");
        int month = Integer.parseInt(parts[0]);
        int year = Integer.parseInt(parts[1]);
        
        // Read all data values
        for (int i = 1; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            if (row != null) {
                Cell cell = row.getCell(colIndex);
                if (cell != null && cell.getCellType() == CellType.NUMERIC) {
                    values.add(cell.getNumericCellValue());
                } else {
                    values.add(Double.NaN); // Missing value
                }
            }
        }
        
        workbook.close();
        fis.close();
        
        // Create TsData with monthly frequency
        TsPeriod start = new TsPeriod(TsFrequency.Monthly, year, month - 1);
        double[] data = values.stream().mapToDouble(Double::doubleValue).toArray();
        
        return new TsData(start, data, true);
    }
    
    /**
     * Alternative method: Create TsData from array if you already have the data
     */
    public static TsData createTimeSeriesFromArray(double[] values, 
                                                   int startYear, 
                                                   int startMonth) {
        // Month is 0-indexed (0 = January, 11 = December)
        TsPeriod start = new TsPeriod(TsFrequency.Monthly, startYear, startMonth);
        return new TsData(start, values, true);
    }
    
    /**
     * Check last observations (Terror functionality)
     */
    public static void checkLastObservations(TsData series, int backCount) {
        try {
            ec.tstoolkit.modelling.arima.CheckLast checkLast = 
                new ec.tstoolkit.modelling.arima.CheckLast(
                    TramoSpecification.TRfull.build());
            
            checkLast.setBackCount(backCount);
            
            if (checkLast.check(series)) {
                System.out.println("\n=== Check Last " + backCount + " Observations ===");
                
                double[] scores = checkLast.getScores();
                double[] forecasts = checkLast.getForecastsValues();
                double[] actuals = checkLast.getActualValues();
                
                for (int i = 0; i < backCount; i++) {
                    System.out.println("Period " + (i+1) + " from end:");
                    System.out.println("  Actual: " + actuals[i]);
                    System.out.println("  Forecast: " + forecasts[i]);
                    System.out.println("  Score: " + scores[i]);
                    
                    // Classify based on score
                    if (Math.abs(scores[i]) > 5) {
                        System.out.println("  Status: LIKELY ERROR (red)");
                    } else if (Math.abs(scores[i]) > 4) {
                        System.out.println("  Status: POSSIBLE ERROR (orange)");
                    } else {
                        System.out.println("  Status: OK");
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}