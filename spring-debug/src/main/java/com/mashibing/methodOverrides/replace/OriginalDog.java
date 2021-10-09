package com.mashibing.methodOverrides.replace;

public class OriginalDog {
	public void sayHello() {
		System.out.println("Hello,I am a black dog...");
	}

	public void sayHello(String name) {
		System.out.println("Hello,I am a black dog, my name is " + name);
	}
}