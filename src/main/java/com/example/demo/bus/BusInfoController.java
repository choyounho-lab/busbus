package com.example.demo.bus;

import java.net.URI;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class BusInfoController {

	private final SeleniumBusInfoService busInfoService;
	private final NaverBusInfoService naverBusInfoService;
	private final TagoBusInfoService tagoBusInfoService;
	private final TagoBusStationService tagoBusStationService;

	public BusInfoController(
		SeleniumBusInfoService busInfoService,
		NaverBusInfoService naverBusInfoService,
		TagoBusInfoService tagoBusInfoService,
		TagoBusStationService tagoBusStationService
	) {
		this.busInfoService = busInfoService;
		this.naverBusInfoService = naverBusInfoService;
		this.tagoBusInfoService = tagoBusInfoService;
		this.tagoBusStationService = tagoBusStationService;
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

	@GetMapping("/api/bus/tago/route")
	public BusRouteInfo tagoRoute(
		@RequestParam(required = false) String serviceKey,
		@RequestParam(required = false) String cityCode,
		@RequestParam(required = false) String routeId,
		@RequestParam(required = false) String busNumber,
		@RequestParam(required = false) String region
	) {
		if (cityCode != null && routeId != null) {
			return tagoBusInfoService.fetchRouteInfo(routeId, cityCode, serviceKey);
		}
		return tagoBusInfoService.fetchRouteInfoByRouteNo(busNumber, region, serviceKey);
	}

	@GetMapping("/api/bus/tago/cities")
	public java.util.List<TagoCityCode> tagoCities(@RequestParam(required = false) String serviceKey) {
		return tagoBusInfoService.fetchCityCodes(serviceKey);
	}

	@GetMapping("/api/bus/tago/routes")
	public java.util.List<TagoRouteSummary> tagoRoutes(
		@RequestParam(required = false) String serviceKey,
		@RequestParam String cityCode,
		@RequestParam(required = false) String busNumber
	) {
		return tagoBusInfoService.fetchRoutes(cityCode, busNumber, serviceKey);
	}

	@GetMapping("/api/bus/tago/stations/nearby")
	public java.util.List<TagoBusStation> tagoNearbyStations(
		@RequestParam(required = false) String serviceKey,
		@RequestParam String latitude,
		@RequestParam String longitude
	) {
		return tagoBusStationService.fetchNearbyStations(latitude, longitude, serviceKey);
	}
}
