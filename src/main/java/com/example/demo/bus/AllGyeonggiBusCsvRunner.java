package com.example.demo.bus;

import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** Builds a resumable, all-route Gyeonggi dataset from Naver, Kakao, and TAGO. */
@Component
@Order(100)
public class AllGyeonggiBusCsvRunner implements ApplicationRunner {

	private static final List<String> FIELDS = List.of(
		"제공처", "버스유형", "출발지", "도착지", "평일_첫차", "평일_막차",
		"토요일_첫차", "토요일_막차", "일요일_첫차", "일요일_막차",
		"휴일_첫차", "휴일_막차", "평일_배차간격", "토요일_배차간격",
		"일요일_배차간격", "휴일_배차간격", "평일_배차횟수", "토요일_배차횟수",
		"일요일_배차횟수", "휴일_배차횟수"
	);

	private final TagoBusInfoService tago;
	private final NaverBusInfoService naver;
	private final SeleniumBusInfoService kakao;
	private final ConfigurableApplicationContext context;
	private Map<String, CachedPair> cache = Map.of();

	public AllGyeonggiBusCsvRunner(TagoBusInfoService tago, NaverBusInfoService naver,
		SeleniumBusInfoService kakao, ConfigurableApplicationContext context) {
		this.tago = tago;
		this.naver = naver;
		this.kakao = kakao;
		this.context = context;
	}

	@Override
	public void run(ApplicationArguments args) throws Exception {
		if (!args.containsOption("fill-all-gyeonggi-buses")) {
			return;
		}
		Path output = Path.of(option(args, "output", "all_gyeonggi_buses_naver_kakao_tago.csv"));
		int workers = Integer.parseInt(option(args, "workers", "6"));
		String serviceKey = option(args, "tago-service-key", "");
		Path cachePath = Path.of(option(args, "cache", "bus_routes_batch_20260814_three_sources_final.csv"));
		cache = loadCache(cachePath);
		Set<String> completed = completedKeys(output);
		List<RouteSeed> seeds = routeSeeds(serviceKey);
		boolean append = Files.exists(output) && Files.size(output) > 0;

		try (BufferedWriter writer = Files.newBufferedWriter(output, StandardCharsets.UTF_8,
			StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
			if (!append) {
				writer.write('\ufeff');
				writer.write(csv(header()));
				writer.newLine();
				writer.flush();
			}

			AtomicInteger done = new AtomicInteger(completed.size());
			ExecutorService pool = Executors.newFixedThreadPool(workers);
			try {
				List<CompletableFuture<Void>> futures = seeds.stream()
					.filter(seed -> !completed.contains(seed.key()))
					.map(seed -> CompletableFuture.runAsync(() -> {
						List<String> row = collect(seed, serviceKey);
						synchronized (writer) {
							try {
								writer.write(csv(row));
								writer.newLine();
								writer.flush();
							} catch (Exception ex) {
								throw new IllegalStateException("Failed to save " + seed.key(), ex);
							}
						}
						System.out.println("Collected " + done.incrementAndGet() + "/" + seeds.size()
							+ ": " + seed.region() + " " + seed.routeNo());
					}, pool))
					.toList();
				CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();
			} finally {
				pool.shutdownNow();
			}
		} finally {
			SpringApplication.exit(context, () -> 0);
		}
	}

	private List<RouteSeed> routeSeeds(String serviceKey) {
		Map<String, RouteSeed> unique = new LinkedHashMap<>();
		for (TagoCityCode city : tago.fetchCityCodes(serviceKey)) {
			if (!city.cityCode().startsWith("31")) {
				continue;
			}
			for (TagoRouteSummary route : tago.fetchRoutes(city.cityCode(), serviceKey)) {
				RouteSeed seed = new RouteSeed(route.routeId(), route.routeNo(), city.cityCode(), city.cityName(), route);
				unique.putIfAbsent(seed.key(), seed);
			}
		}
		System.out.println("Discovered " + unique.size() + " Gyeonggi routes from TAGO.");
		return new ArrayList<>(unique.values());
	}

	private List<String> collect(RouteSeed seed, String serviceKey) {
		BusRouteInfo tagoInfo = safe("TAGO", seed, () -> tago.fetchRouteInfo(seed.routeId(), seed.cityCode(), serviceKey));
		if (tagoInfo == null) {
			tagoInfo = new BusRouteInfo("", "TAGO", seed.summary().routeType(), seed.summary().origin(),
				seed.summary().destination(), "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", List.of());
		}
		CachedPair cached = cache.get(seed.routeId());
		if (cached == null) cached = cache.get(cacheKey(seed.region(), seed.routeNo()));
		BusRouteInfo naverInfo = cached == null ? null : cached.naver();
		BusRouteInfo kakaoInfo = cached == null ? null : cached.kakao();
		if (!hasData(naverInfo)) {
			naverInfo = safe("Naver", seed, () -> naver.fetchRouteInfo(seed.routeNo(), seed.region()));
		}
		if (!hasData(kakaoInfo)) {
			kakaoInfo = safe("Kakao", seed, () -> kakao.fetchKakaoBusRouteInfo(seed.routeNo(), seed.region()));
		}

		List<String> row = new ArrayList<>();
		row.add(seed.routeId());
		row.add(seed.routeNo());
		row.add(seed.cityCode());
		row.add(seed.region());
		addInfo(row, "네이버", naverInfo);
		addInfo(row, "카카오", kakaoInfo);
		addInfo(row, "TAGO", tagoInfo);
		List<String> sources = new ArrayList<>();
		if (hasData(naverInfo)) sources.add("네이버");
		if (hasData(kakaoInfo)) sources.add("카카오");
		if (hasData(tagoInfo)) sources.add("TAGO");
		row.add(String.join("|", sources));
		return row;
	}

	private void addInfo(List<String> row, String source, BusRouteInfo info) {
		row.add(hasData(info) ? source : "");
		row.add(v(info, i -> i.busType()));
		row.add(v(info, i -> i.origin()));
		row.add(v(info, i -> i.destination()));
		row.add(v(info, i -> i.firstBus()));
		row.add(v(info, i -> i.lastBus()));
		row.add(v(info, i -> i.saturdayFirstBus()));
		row.add(v(info, i -> i.saturdayLastBus()));
		row.add(v(info, i -> i.sundayFirstBus()));
		row.add(v(info, i -> i.sundayLastBus()));
		row.add(v(info, i -> i.holidayFirstBus()));
		row.add(v(info, i -> i.holidayLastBus()));
		row.add(v(info, i -> i.headway()));
		row.add(v(info, i -> i.saturdayHeadway()));
		row.add(v(info, i -> i.sundayHeadway()));
		row.add(v(info, i -> i.holidayHeadway()));
		row.add(v(info, i -> i.dailyRuns()));
		row.add(v(info, i -> i.saturdayRuns()));
		row.add(v(info, i -> i.sundayRuns()));
		row.add(v(info, i -> i.holidayRuns()));
	}

	private List<String> header() {
		List<String> header = new ArrayList<>(List.of("노선ID", "버스번호", "지역코드", "지역"));
		for (String source : List.of("네이버", "카카오", "TAGO")) {
			for (String field : FIELDS) header.add(source + "_" + field);
		}
		header.add("정보제공처");
		return header;
	}

	private Set<String> completedKeys(Path output) throws Exception {
		Set<String> keys = new LinkedHashSet<>();
		if (!Files.exists(output)) return keys;
		try (var lines = Files.lines(output, StandardCharsets.UTF_8)) {
			lines.skip(1).map(this::firstCsvValue).filter(s -> !s.isBlank()).forEach(keys::add);
		}
		return keys;
	}

	private Map<String, CachedPair> loadCache(Path path) throws Exception {
		if (!Files.exists(path)) return Map.of();
		List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
		if (lines.isEmpty()) return Map.of();
		List<String> headers = parseCsv(lines.get(0).replace("\ufeff", ""));
		Map<String, CachedPair> result = new LinkedHashMap<>();
		for (int i = 1; i < lines.size(); i++) {
			List<String> values = parseCsv(lines.get(i));
			String routeId = cell(headers, values, "rte_id");
			if (routeId.isBlank()) continue;
			CachedPair pair = new CachedPair(cachedInfo(headers, values, "네이버"), cachedInfo(headers, values, "카카오"));
			result.put(routeId, pair);
			result.putIfAbsent(cacheKey(cell(headers, values, "지역"), cell(headers, values, "버스번호")), pair);
		}
		System.out.println("Loaded " + result.size() + " prior Naver/Kakao route rows.");
		return result;
	}

	private BusRouteInfo cachedInfo(List<String> headers, List<String> values, String source) {
		return new BusRouteInfo("", source,
			cell(headers, values, source + "_버스유형"), cell(headers, values, source + "_출발지"),
			cell(headers, values, source + "_도착지"), cell(headers, values, source + "_평일_첫차"),
			cell(headers, values, source + "_평일_막차"), cell(headers, values, source + "_토요일_첫차"),
			cell(headers, values, source + "_토요일_막차"), cell(headers, values, source + "_일요일_첫차"),
			cell(headers, values, source + "_일요일_막차"), cell(headers, values, source + "_휴일_첫차"),
			cell(headers, values, source + "_휴일_막차"), cell(headers, values, source + "_평일_배차간격"),
			cell(headers, values, source + "_평일_배차횟수"), cell(headers, values, source + "_토요일_배차간격"),
			cell(headers, values, source + "_토요일_배차횟수"), cell(headers, values, source + "_일요일_배차간격"),
			cell(headers, values, source + "_일요일_배차횟수"), cell(headers, values, source + "_휴일_배차간격"),
			cell(headers, values, source + "_휴일_배차횟수"), List.of());
	}

	private String cell(List<String> headers, List<String> values, String name) {
		int index = headers.indexOf(name);
		return index < 0 || index >= values.size() ? "" : values.get(index);
	}

	private String cacheKey(String region, String routeNo) {
		String normalized = routeNo == null ? "" : routeNo.replaceAll("\\([^)]*\\)", "").replaceAll("\\s+", "");
		return "route:" + (region == null ? "" : region.replaceAll("\\s+", "")) + ":" + normalized;
	}

	private List<String> parseCsv(String line) {
		List<String> values = new ArrayList<>();
		StringBuilder value = new StringBuilder();
		boolean quoted = false;
		for (int i = 0; i < line.length(); i++) {
			char ch = line.charAt(i);
			if (ch == '"') {
				if (quoted && i + 1 < line.length() && line.charAt(i + 1) == '"') {
					value.append('"'); i++;
				} else quoted = !quoted;
			} else if (ch == ',' && !quoted) {
				values.add(value.toString()); value.setLength(0);
			} else value.append(ch);
		}
		values.add(value.toString());
		return values;
	}

	private String firstCsvValue(String line) {
		if (line.startsWith("\"")) {
			int end = line.indexOf('\"', 1);
			return end < 0 ? "" : line.substring(1, end).replace("\"\"", "\"");
		}
		int comma = line.indexOf(',');
		return comma < 0 ? line : line.substring(0, comma);
	}

	private <T> T safe(String source, RouteSeed seed, CheckedSupplier<T> action) {
		try {
			return action.get();
		} catch (Exception ex) {
			System.err.println(source + " failed " + seed.key() + " " + seed.region() + " " + seed.routeNo()
				+ ": " + ex.getMessage());
			return null;
		}
	}

	private boolean hasData(BusRouteInfo info) {
		return info != null && !(blank(info.busType()) && blank(info.origin()) && blank(info.destination())
			&& blank(info.firstBus()) && blank(info.lastBus()) && blank(info.headway()));
	}

	private String v(BusRouteInfo info, Value value) {
		return info == null || value.get(info) == null ? "" : value.get(info);
	}

	private boolean blank(String value) { return value == null || value.isBlank(); }

	private String csv(List<String> values) {
		return values.stream().map(value -> "\"" + (value == null ? "" : value.replace("\"", "\"\"")) + "\"")
			.collect(java.util.stream.Collectors.joining(","));
	}

	private String option(ApplicationArguments args, String name, String fallback) {
		List<String> values = args.getOptionValues(name);
		return values == null || values.isEmpty() || values.get(0).isBlank() ? fallback : values.get(0);
	}

	private record RouteSeed(String routeId, String routeNo, String cityCode, String region, TagoRouteSummary summary) {
		String key() { return routeId; }
	}
	private record CachedPair(BusRouteInfo naver, BusRouteInfo kakao) { }

	@FunctionalInterface private interface CheckedSupplier<T> { T get() throws Exception; }
	@FunctionalInterface private interface Value { String get(BusRouteInfo info); }
}
