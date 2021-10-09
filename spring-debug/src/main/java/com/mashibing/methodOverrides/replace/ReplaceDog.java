package com.mashibing.methodOverrides.replace;

import org.springframework.beans.factory.support.MethodReplacer;

import java.lang.reflect.Method;
import java.util.Arrays;

public class ReplaceDog implements MethodReplacer {
	@Override
	public Object reimplement(Object obj, Method method, Object[] args) throws Throwable {
		System.out.println("Hello, I am a white dog...");

		Arrays.stream(args).forEach(str -> System.out.println("参数:" + str));
		return obj;
	}
}