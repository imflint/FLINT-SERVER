package kr.flint.shared.storage;

public interface StorageUrlProvider {

    StorageUploadUrl generateUploadUrl(String key, String contentType);

}
