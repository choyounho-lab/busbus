package com.example.demo.bus;

public record TagoRouteSummary(
	String routeId,
	String routeNo,
	String routeType,
	String origin,
	String destination,
	String raw
) {
}
