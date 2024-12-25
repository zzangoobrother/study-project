package com.example.csvdb.jdbc;

import java.io.*;
import java.util.*;

public class CsvFileManager {

    public static void writeData(String tableName, List<String> columns, List<List<String>> values) throws IOException {
        File file = new File(tableName + ".csv");
        boolean isNewFile = !file.exists();

        if (isNewFile) {
            throw new RuntimeException("Table이 존재하지 않습니다.");
        }

        BufferedReader br = new BufferedReader(new FileReader(file));

        String[] rows = br.readLine().split(",");
        for (int i = 0; i < rows.length; i++) {
            rows[i] = rows[i].trim();
        }

        Set<Integer> columnIndexes = new HashSet<>();

        for (String column : columns) {
            for (int i = 0; i < rows.length; i++) {
                if (rows[i].equalsIgnoreCase(column)) {
                    columnIndexes.add(i);
                }
            }
        }

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file, true))) {
            for (List<String> value : values) {
                for (int i = 0; i < rows.length; i++) {
                    if (!columnIndexes.contains(i)) {
                        writer.write("null");
                    } else {
                        writer.write(value.get(i));
                    }

                    if (i != rows.length - 1) {
                        writer.write(",");
                    }
                }

                writer.newLine();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void dropTable(String tableName) {
        File file = new File(tableName + ".csv");
        if (file.exists()) {
            file.delete();
        }
    }

    public static void createTable(String tableName, List<String> columns) {
        File file = new File(tableName + ".csv");
        if (file.exists()) {
            dropTable(tableName);
            System.out.println("Table already exists.");
        }

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
            writer.write(String.join(",", columns));
            writer.newLine();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static List<Map<String, String>> readTable(String tableName) {
        List<Map<String, String>> results = new ArrayList<>();
        File file = new File(tableName + ".csv");

        if (!file.exists()) {
            throw new RuntimeException("Table does not exist.");
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String headerLine = reader.readLine();
            if (headerLine == null) {
                return results;
            }

            List<String> columns = Arrays.asList(headerLine.split(","));
            String line;
            while ((line = reader.readLine()) != null) {
                String[] values = line.split(",");
                Map<String, String> row = new HashMap<>();
                for (int i = 0; i < values.length; i++) {
                    row.put(columns.get(i).trim(), values[i].trim());
                }
                results.add(row);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        return results;
    }
}
