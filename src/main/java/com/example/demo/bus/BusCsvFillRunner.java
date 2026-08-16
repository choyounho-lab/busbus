package com.example.demo.bus;

import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

@Component
public class BusCsvFillRunner implements ApplicationRunner {

	private static final Path DEFAULT_INPUT = Path.of("bus_routes.csv");
	private static final Path DEFAULT_OUTPUT = Path.of(System.getProperty("user.home"), "Desktop", "bus_routes_multi_source.csv");

	private final SeleniumBusInfoService busInfoService;
	private final NaverBusInfoService naverBusInfoService;
	private final GbisBusInfoService gbisBusInfoService;
	private final TagoBusInfoService tagoBusInfoService;
	private final ConfigurableApplicationContext context;

	public BusCsvFillRunner(
		SeleniumBusInfoService busInfoService,
		NaverBusInfoService naverBusInfoService,
		GbisBusInfoService gbisBusInfoService,
		TagoBusInfoService tagoBusInfoService,
		ConfigurableApplicationContext context
	) {
		this.busInfoService = busInfoService;
		this.naverBusInfoService = naverBusInfoService;
		this.gbisBusInfoService = gbisBusInfoService;
		this.tagoBusInfoService = tagoBusInfoService;
		this.context = context;
	}

	@Override
	public void run(ApplicationArguments args) throws Exception {
		if (!args.containsOption("fill-bus-csv")) {
			return;
		}

		Path input = pathOption(args, "input", DEFAULT_INPUT);
		Path output = pathOption(args, "output", DEFAULT_OUTPUT);
		List<String> rows = Files.readAllLines(input, StandardCharsets.UTF_8);
		int limit = args.getOptionValues("fill-limit") == null
			? rows.size() - 1
			: Integer.parseInt(args.getOptionValues("fill-limit").get(0));
		String gbisServiceKey = args.getOptionValues("gbis-service-key") == null
			? ""
			: args.getOptionValues("gbis-service-key").get(0);
		String tagoServiceKey = args.getOptionValues("tago-service-key") == null
			? ""
			: args.getOptionValues("tago-service-key").get(0);
		String tagoCityCodeOption = args.getOptionValues("tago-city-code") == null
			? ""
			: args.getOptionValues("tago-city-code").get(0);
		boolean skipKakao = args.containsOption("skip-kakao");
		boolean skipNaver = args.containsOption("skip-naver");
		boolean skipTago = args.containsOption("skip-tago");

		try (BufferedWriter writer = Files.newBufferedWriter(output, StandardCharsets.UTF_8)) {
				writer.write(String.join(",",
					"rte_id",
					"버스번호",
					"지역코드",
					"지역",
					"담당자",
					"GBIS_제공처",
					"GBIS_버스유형",
					"GBIS_출발지",
					"GBIS_도착지",
					"GBIS_평일_첫차",
					"GBIS_평일_막차",
					"GBIS_토요일_첫차",
					"GBIS_토요일_막차",
					"GBIS_일요일_첫차",
					"GBIS_일요일_막차",
					"GBIS_휴일_첫차",
					"GBIS_휴일_막차",
					"GBIS_평일_배차간격",
					"GBIS_토요일_배차간격",
					"GBIS_일요일_배차간격",
					"GBIS_휴일_배차간격",
					"GBIS_평일_배차횟수",
					"GBIS_토요일_배차횟수",
					"GBIS_일요일_배차횟수",
					"GBIS_휴일_배차횟수",
					"TAGO_제공처",
					"TAGO_버스유형",
					"TAGO_출발지",
					"TAGO_도착지",
					"TAGO_평일_첫차",
					"TAGO_평일_막차",
					"TAGO_토요일_첫차",
					"TAGO_토요일_막차",
					"TAGO_일요일_첫차",
					"TAGO_일요일_막차",
					"TAGO_휴일_첫차",
					"TAGO_휴일_막차",
					"TAGO_평일_배차간격",
					"TAGO_토요일_배차간격",
					"TAGO_일요일_배차간격",
					"TAGO_휴일_배차간격",
					"TAGO_평일_배차횟수",
					"TAGO_토요일_배차횟수",
					"TAGO_일요일_배차횟수",
					"TAGO_휴일_배차횟수",
					"카카오_제공처",
					"카카오_버스유형",
					"카카오_출발지",
					"카카오_도착지",
					"카카오_평일_첫차",
					"카카오_평일_막차",
					"카카오_토요일_첫차",
					"카카오_토요일_막차",
					"카카오_일요일_첫차",
					"카카오_일요일_막차",
					"카카오_휴일_첫차",
					"카카오_휴일_막차",
					"카카오_평일_배차간격",
					"카카오_토요일_배차간격",
					"카카오_일요일_배차간격",
					"카카오_휴일_배차간격",
					"카카오_평일_배차횟수",
					"카카오_토요일_배차횟수",
					"카카오_일요일_배차횟수",
					"카카오_휴일_배차횟수",
					"네이버_제공처",
					"네이버_버스유형",
					"네이버_출발지",
					"네이버_도착지",
					"네이버_평일_첫차",
					"네이버_평일_막차",
					"네이버_토요일_첫차",
					"네이버_토요일_막차",
					"네이버_일요일_첫차",
					"네이버_일요일_막차",
					"네이버_휴일_첫차",
					"네이버_휴일_막차",
					"네이버_평일_배차간격",
					"네이버_토요일_배차간격",
					"네이버_일요일_배차간격",
					"네이버_휴일_배차간격",
					"네이버_평일_배차횟수",
					"네이버_토요일_배차횟수",
					"네이버_일요일_배차횟수",
					"네이버_휴일_배차횟수",
					"정보제공처"
				));
			writer.newLine();

			List<String> headerColumns = parseCsvRow(rows.get(0));
			for (int i = 1; i < rows.size() && i <= limit; i++) {
				List<String> columns = parseCsvRow(rows.get(i));
				String routeId = columns.get(0);
				String busNumber = columns.get(1);
				String regionCode = columns.get(2);
				String region = columns.get(3);
				String owner = columns.get(4);
				String tagoRouteId = firstNonBlank(
					columnValue(headerColumns, columns, "TAGO_노선ID"),
					columnValue(headerColumns, columns, "tago_route_id"),
					routeId
				);
				String tagoCityCode = firstNonBlank(
					columnValue(headerColumns, columns, "TAGO_도시코드"),
					columnValue(headerColumns, columns, "tago_city_code"),
					tagoCityCodeOption
				);

				BusRouteInfo gbisInfo = null;
				BusRouteInfo tagoInfo = null;
				BusRouteInfo kakaoInfo = null;
				BusRouteInfo naverInfo = null;
				try {
					if (!gbisServiceKey.isBlank()) {
						gbisInfo = gbisBusInfoService.fetchRouteInfo(routeId, gbisServiceKey);
					}
				} catch (RuntimeException ex) {
					System.err.println("GBIS failed " + routeId + " " + region + " " + busNumber + ": " + ex.getMessage());
				}
				if (!skipTago && !tagoServiceKey.isBlank()) {
					try {
						tagoInfo = tagoCityCode.isBlank()
							? tagoBusInfoService.fetchRouteInfoByRouteNo(busNumber, region, tagoServiceKey)
							: tagoBusInfoService.fetchRouteInfo(tagoRouteId, tagoCityCode, tagoServiceKey);
					} catch (RuntimeException ex) {
						System.err.println("TAGO failed " + routeId + " " + region + " " + busNumber + ": " + ex.getMessage());
					}
				}
				if (!skipKakao) {
					try {
						kakaoInfo = busInfoService.fetchKakaoBusRouteInfo(busNumber, region);
					} catch (RuntimeException ex) {
						System.err.println("Kakao failed " + routeId + " " + region + " " + busNumber + ": " + ex.getMessage());
					}
				}
				if (!skipNaver) {
					try {
						naverInfo = naverBusInfoService.fetchRouteInfo(busNumber, region);
						if (!isConsistentRoute(naverInfo, gbisInfo, kakaoInfo)) {
							System.err.println("Naver route mismatch " + routeId + " " + region + " " + busNumber);
							naverInfo = null;
						}
					} catch (RuntimeException ex) {
						System.err.println("Naver failed " + routeId + " " + region + " " + busNumber + ": " + ex.getMessage());
					}
				}

					writer.write(toCsvRow(List.of(
						routeId,
						busNumber,
						regionCode,
						region,
						owner,
						gbisInfo == null ? "" : "GBIS",
						valueOrBlank(gbisInfo == null ? null : gbisInfo.busType()),
						valueOrBlank(gbisInfo == null ? null : gbisInfo.origin()),
						valueOrBlank(gbisInfo == null ? null : gbisInfo.destination()),
						valueOrBlank(gbisInfo == null ? null : gbisInfo.firstBus()),
						valueOrBlank(gbisInfo == null ? null : gbisInfo.lastBus()),
						valueOrBlank(gbisInfo == null ? null : gbisInfo.saturdayFirstBus()),
						valueOrBlank(gbisInfo == null ? null : gbisInfo.saturdayLastBus()),
						valueOrBlank(gbisInfo == null ? null : gbisInfo.sundayFirstBus()),
						valueOrBlank(gbisInfo == null ? null : gbisInfo.sundayLastBus()),
						valueOrBlank(gbisInfo == null ? null : gbisInfo.holidayFirstBus()),
						valueOrBlank(gbisInfo == null ? null : gbisInfo.holidayLastBus()),
						valueOrBlank(gbisInfo == null ? null : gbisInfo.headway()),
						valueOrBlank(gbisInfo == null ? null : gbisInfo.saturdayHeadway()),
						valueOrBlank(gbisInfo == null ? null : gbisInfo.sundayHeadway()),
						valueOrBlank(gbisInfo == null ? null : gbisInfo.holidayHeadway()),
						valueOrBlank(gbisInfo == null ? null : gbisInfo.dailyRuns()),
						valueOrBlank(gbisInfo == null ? null : gbisInfo.saturdayRuns()),
						valueOrBlank(gbisInfo == null ? null : gbisInfo.sundayRuns()),
						valueOrBlank(gbisInfo == null ? null : gbisInfo.holidayRuns()),
						tagoInfo == null ? "" : "TAGO",
						valueOrBlank(tagoInfo == null ? null : tagoInfo.busType()),
						valueOrBlank(tagoInfo == null ? null : tagoInfo.origin()),
						valueOrBlank(tagoInfo == null ? null : tagoInfo.destination()),
						valueOrBlank(tagoInfo == null ? null : tagoInfo.firstBus()),
						valueOrBlank(tagoInfo == null ? null : tagoInfo.lastBus()),
						valueOrBlank(tagoInfo == null ? null : tagoInfo.saturdayFirstBus()),
						valueOrBlank(tagoInfo == null ? null : tagoInfo.saturdayLastBus()),
						valueOrBlank(tagoInfo == null ? null : tagoInfo.sundayFirstBus()),
						valueOrBlank(tagoInfo == null ? null : tagoInfo.sundayLastBus()),
						valueOrBlank(tagoInfo == null ? null : tagoInfo.holidayFirstBus()),
						valueOrBlank(tagoInfo == null ? null : tagoInfo.holidayLastBus()),
						valueOrBlank(tagoInfo == null ? null : tagoInfo.headway()),
						valueOrBlank(tagoInfo == null ? null : tagoInfo.saturdayHeadway()),
						valueOrBlank(tagoInfo == null ? null : tagoInfo.sundayHeadway()),
						valueOrBlank(tagoInfo == null ? null : tagoInfo.holidayHeadway()),
						valueOrBlank(tagoInfo == null ? null : tagoInfo.dailyRuns()),
						valueOrBlank(tagoInfo == null ? null : tagoInfo.saturdayRuns()),
						valueOrBlank(tagoInfo == null ? null : tagoInfo.sundayRuns()),
						valueOrBlank(tagoInfo == null ? null : tagoInfo.holidayRuns()),
						kakaoInfo == null ? "" : "카카오",
						valueOrBlank(kakaoInfo == null ? null : kakaoInfo.busType()),
						valueOrBlank(kakaoInfo == null ? null : kakaoInfo.origin()),
						valueOrBlank(kakaoInfo == null ? null : kakaoInfo.destination()),
						valueOrBlank(kakaoInfo == null ? null : kakaoInfo.firstBus()),
						valueOrBlank(kakaoInfo == null ? null : kakaoInfo.lastBus()),
						valueOrBlank(kakaoInfo == null ? null : kakaoInfo.saturdayFirstBus()),
						valueOrBlank(kakaoInfo == null ? null : kakaoInfo.saturdayLastBus()),
						valueOrBlank(kakaoInfo == null ? null : kakaoInfo.sundayFirstBus()),
						valueOrBlank(kakaoInfo == null ? null : kakaoInfo.sundayLastBus()),
						valueOrBlank(kakaoInfo == null ? null : kakaoInfo.holidayFirstBus()),
						valueOrBlank(kakaoInfo == null ? null : kakaoInfo.holidayLastBus()),
						valueOrBlank(kakaoInfo == null ? null : kakaoInfo.headway()),
						valueOrBlank(kakaoInfo == null ? null : kakaoInfo.saturdayHeadway()),
						valueOrBlank(kakaoInfo == null ? null : kakaoInfo.sundayHeadway()),
						valueOrBlank(kakaoInfo == null ? null : kakaoInfo.holidayHeadway()),
						valueOrBlank(kakaoInfo == null ? null : kakaoInfo.dailyRuns()),
						valueOrBlank(kakaoInfo == null ? null : kakaoInfo.saturdayRuns()),
						valueOrBlank(kakaoInfo == null ? null : kakaoInfo.sundayRuns()),
						valueOrBlank(kakaoInfo == null ? null : kakaoInfo.holidayRuns()),
						hasRouteData(naverInfo) ? "네이버" : "",
						valueOrBlank(naverInfo == null ? null : naverInfo.busType()),
						naverValueOrTrusted(naverInfo, gbisInfo, kakaoInfo, BusRouteInfo::origin),
						naverValueOrTrusted(naverInfo, gbisInfo, kakaoInfo, BusRouteInfo::destination),
						valueOrBlank(naverInfo == null ? null : naverInfo.firstBus()),
						valueOrBlank(naverInfo == null ? null : naverInfo.lastBus()),
						valueOrBlank(naverInfo == null ? null : naverInfo.saturdayFirstBus()),
						valueOrBlank(naverInfo == null ? null : naverInfo.saturdayLastBus()),
						valueOrBlank(naverInfo == null ? null : naverInfo.sundayFirstBus()),
						valueOrBlank(naverInfo == null ? null : naverInfo.sundayLastBus()),
						valueOrBlank(naverInfo == null ? null : naverInfo.holidayFirstBus()),
						valueOrBlank(naverInfo == null ? null : naverInfo.holidayLastBus()),
						valueOrBlank(naverInfo == null ? null : naverInfo.headway()),
						valueOrBlank(naverInfo == null ? null : naverInfo.saturdayHeadway()),
						valueOrBlank(naverInfo == null ? null : naverInfo.sundayHeadway()),
						valueOrBlank(naverInfo == null ? null : naverInfo.holidayHeadway()),
						valueOrBlank(naverInfo == null ? null : naverInfo.dailyRuns()),
						valueOrBlank(naverInfo == null ? null : naverInfo.saturdayRuns()),
						valueOrBlank(naverInfo == null ? null : naverInfo.sundayRuns()),
						valueOrBlank(naverInfo == null ? null : naverInfo.holidayRuns()),
						sourceList(gbisInfo, tagoInfo, kakaoInfo, naverInfo)
					)));
				writer.newLine();
				writer.flush();

				System.out.println("Filled " + i + "/" + (rows.size() - 1) + ": " + region + " " + busNumber);
			}
		} finally {
			SpringApplication.exit(context, () -> 0);
		}
	}

	private Path pathOption(ApplicationArguments args, String name, Path fallback) {
		List<String> values = args.getOptionValues(name);
		if (values == null || values.isEmpty() || values.get(0).isBlank()) {
			return fallback;
		}
		return Path.of(values.get(0));
	}

	private List<String> parseCsvRow(String row) {
		String[] values = row.split(",", -1);
		List<String> columns = new ArrayList<>(values.length);
		for (String value : values) {
			columns.add(value);
		}
		return columns;
	}

	private String sourceList(BusRouteInfo gbisInfo, BusRouteInfo tagoInfo, BusRouteInfo kakaoInfo, BusRouteInfo naverInfo) {
		List<String> sources = new ArrayList<>();
		if (gbisInfo != null) {
			sources.add("GBIS");
		}
		if (tagoInfo != null) {
			sources.add("TAGO");
		}
		if (kakaoInfo != null) {
			sources.add("카카오");
		}
		if (naverInfo != null) {
			if (!hasRouteData(naverInfo)) {
				return String.join("|", sources);
			}
			sources.add("네이버");
		}
		return String.join("|", sources);
	}

	private boolean hasRouteData(BusRouteInfo info) {
		if (info == null) {
			return false;
		}
		return !valueOrBlank(info.busType()).isBlank()
			|| !valueOrBlank(info.origin()).isBlank()
			|| !valueOrBlank(info.destination()).isBlank()
			|| !valueOrBlank(info.firstBus()).isBlank()
			|| !valueOrBlank(info.lastBus()).isBlank()
			|| !valueOrBlank(info.headway()).isBlank();
	}

	private String naverValueOrTrusted(
		BusRouteInfo naverInfo,
		BusRouteInfo gbisInfo,
		BusRouteInfo kakaoInfo,
		Function<BusRouteInfo, String> extractor
	) {
		if (!hasRouteData(naverInfo)) {
			return "";
		}

		String value = valueOrBlank(extractor.apply(naverInfo));
		if (!value.isBlank()) {
			return value;
		}

		value = valueOrBlank(gbisInfo == null ? null : extractor.apply(gbisInfo));
		if (!value.isBlank()) {
			return value;
		}

		return valueOrBlank(kakaoInfo == null ? null : extractor.apply(kakaoInfo));
	}

	private boolean isConsistentRoute(BusRouteInfo candidate, BusRouteInfo... trustedInfos) {
		if (!hasRouteData(candidate)) {
			return false;
		}

		String candidateOrigin = normalized(candidate.origin());
		String candidateDestination = normalized(candidate.destination());
		if (candidateOrigin.isBlank() || candidateDestination.isBlank()) {
			return true;
		}

		for (BusRouteInfo trustedInfo : trustedInfos) {
			if (!hasRouteData(trustedInfo)) {
				continue;
			}
			String trustedOrigin = normalized(trustedInfo.origin());
			String trustedDestination = normalized(trustedInfo.destination());
			if (trustedOrigin.isBlank() || trustedDestination.isBlank()) {
				continue;
			}
			if (samePlace(candidateOrigin, trustedOrigin) && samePlace(candidateDestination, trustedDestination)) {
				return true;
			}
		}

		return false;
	}

	private boolean samePlace(String left, String right) {
		return left.contains(right) || right.contains(left);
	}

	private String normalized(String value) {
		return valueOrBlank(value).replaceAll("[\\s.()]", "");
	}

	private String toCsvRow(List<String> values) {
		return values.stream()
			.map(this::escapeCsv)
			.reduce((left, right) -> left + "," + right)
			.orElse("");
	}

	private String escapeCsv(String value) {
		if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
			return "\"" + value.replace("\"", "\"\"") + "\"";
		}
		return value;
	}

	private String valueOrBlank(String value) {
		return value == null ? "" : value;
	}

	private String columnValue(List<String> headerColumns, List<String> columns, String name) {
		int index = headerColumns.indexOf(name);
		if (index < 0 || index >= columns.size()) {
			return "";
		}
		return columns.get(index);
	}

	private String firstNonBlank(String... values) {
		for (String value : values) {
			if (value != null && !value.isBlank()) {
				return value;
			}
		}
		return "";
	}
}
