package net.runelite.client.plugins.microbot.agentserver.handler;

import com.google.gson.Gson;
import com.sun.net.httpserver.HttpExchange;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class EquipmentHandler extends AgentHandler {

	private static final Map<Integer, String> SLOT_NAMES;

	static {
		SLOT_NAMES = new HashMap<>();
		for (EquipmentInventorySlot s : EquipmentInventorySlot.values()) {
			SLOT_NAMES.put(s.getSlotIdx(), s.name());
		}
	}

	public EquipmentHandler(Gson gson) {
		super(gson);
	}

	@Override
	public String getPath() {
		return "/equipment";
	}

	@Override
	protected void handleRequest(HttpExchange exchange) throws IOException {
		try {
			requireGet(exchange);
		} catch (HttpMethodException e) {
			sendJson(exchange, 405, errorResponse(e.getMessage()));
			return;
		}

		List<Map<String, Object>> items = Rs2Equipment.items().stream()
				.map(this::serializeItem)
				.collect(Collectors.toList());

		Map<String, Object> response = new LinkedHashMap<>();
		response.put("count", items.size());
		response.put("items", items);
		sendJson(exchange, 200, response);
	}

	private Map<String, Object> serializeItem(Rs2ItemModel item) {
		Map<String, Object> map = new LinkedHashMap<>();
		map.put("id", item.getId());
		map.put("name", item.getName());
		map.put("slotIndex", item.getSlot());
		map.put("slot", SLOT_NAMES.getOrDefault(item.getSlot(), "UNKNOWN_" + item.getSlot()));
		return map;
	}
}
