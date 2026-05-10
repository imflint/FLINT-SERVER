package kr.flint.infra.discord.dto;

import java.util.ArrayList;
import java.util.List;

public record DiscordEmbed(
	String title,
	String description,
	Integer color,
	String timestamp,
	List<Field> fields
) {
	public record Field(String name, String value, Boolean inline) {
	}

	public static Builder builder() {
		return new Builder();
	}

	public static class Builder {
		private String title;
		private String description;
		private Integer color;
		private String timestamp;
		private final List<Field> fields = new ArrayList<>();

		public Builder title(String title) { this.title = title; return this; }
		public Builder description(String description) { this.description = description; return this; }
		public Builder color(Integer color) { this.color = color; return this; }
		public Builder timestamp(String timestamp) { this.timestamp = timestamp; return this; }
		public Builder field(String name, String value, boolean inline) {
			this.fields.add(new Field(name, value, inline));
			return this;
		}
		public DiscordEmbed build() {
			return new DiscordEmbed(title, description, color, timestamp, fields);
		}
	}
}
