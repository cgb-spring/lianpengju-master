package com.mashibing.selfConverter;

import org.springframework.core.convert.converter.Converter;

public class StudentConverter implements Converter<String,Student> {
    @Override
    public Student convert(String source) {
        System.out.println("-----");
        Student s  = new Student();
        String[] splits = source.split("_");
        s.setId(Integer.parseInt(splits[0]));
        s.setName(splits[1]);
        return s;
    }
}
