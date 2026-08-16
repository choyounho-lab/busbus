package com.example.demo.bus;

public record TagoBusStation(
	String cityCode,
	String nodeId,
	String nodeName,
	String nodeNo,
	String latitude,
	String longitude,
	String raw
) {
}
