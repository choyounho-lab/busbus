package com.example.demo.bus;

import java.util.List;

public record BusInfoSnapshot(
	String url,
	String title,
	List<String> lines
) {
}
