package com.example.demo.bus;

import java.util.List;

public record BusRouteInfo(
	String url,
	String title,
	String busType,
	String origin,
	String destination,
	String firstBus,
	String lastBus,
	String saturdayFirstBus,
	String saturdayLastBus,
	String sundayFirstBus,
	String sundayLastBus,
	String holidayFirstBus,
	String holidayLastBus,
	String headway,
	String dailyRuns,
	String saturdayHeadway,
	String saturdayRuns,
	String sundayHeadway,
	String sundayRuns,
	String holidayHeadway,
	String holidayRuns,
	List<String> rawLines
) {
}
