package com.example.demo.bus;

import java.net.URI;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class BusInfoController {

	private final SeleniumBusInfoService busInfoService;
	private final NaverBusInfoService naverBusInfoService;

	public BusInfoController(SeleniumBusInfoService busInfoService, NaverBusInfoService naverBusInfoService) {
		this.busInfoService = busInfoService;
		this.naverBusInfoService = naverBusInfoService;
	}

	@GetMapping("/api/bus/snapshot")
	public BusInfoSnapshot snapshot(@RequestParam URI url) {
		return busInfoService.fetchSnapshot(url);
	}

	@GetMapping("/api/bus/kakao")
	public BusInfoSnapshot kakao(@RequestParam String query) {
		return busInfoService.fetchKakaoBusInfo(query);
	}

	@GetMapping("/api/bus/kakao/route")
	public BusRouteInfo kakaoRoute(
		@RequestParam(required = false) String query,
		@RequestParam(required = false) String busNumber,
		@RequestParam(required = false) String region
	) {
		if (busNumber != null && region != null) {
			return busInfoService.fetchKakaoBusRouteInfo(busNumber, region);
		}
		return busInfoService.fetchKakaoBusRouteInfo(query);
	}

	@GetMapping("/api/bus/naver/route")
	public BusRouteInfo naverRoute(@RequestParam String busNumber, @RequestParam String region) {
		return naverBusInfoService.fetchRouteInfo(busNumber, region);
	}
}
