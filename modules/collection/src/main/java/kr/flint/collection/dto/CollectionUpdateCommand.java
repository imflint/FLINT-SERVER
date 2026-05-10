package kr.flint.collection.dto;

import java.util.List;

import kr.flint.collection.dto.CollectionCreateCommand.ContentInput;

public record CollectionUpdateCommand(
	String title,
	String description,
	String imageUrl,
	boolean isPublic,
	List<ContentInput> contents
) {
	public static CollectionUpdateCommand of(
		String title,
		String description,
		String imageUrl,
		boolean isPublic,
		List<ContentInput> contents
	) {
		return new CollectionUpdateCommand(title, description, imageUrl, isPublic, contents);
	}
}
