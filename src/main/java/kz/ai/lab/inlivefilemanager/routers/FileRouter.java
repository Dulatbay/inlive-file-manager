package kz.ai.lab.inlivefilemanager.routers;

import io.micrometer.common.lang.NonNull;
import io.micrometer.common.util.StringUtils;
import kz.ai.lab.inlivefilemanager.dto.S3Blob;
import kz.ai.lab.inlivefilemanager.services.StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.MediaType;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.http.codec.multipart.Part;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

import static org.springframework.web.reactive.function.server.RouterFunctions.route;

@Slf4j
@Component
@RequiredArgsConstructor
public class FileRouter {
    private final StorageService storageService;

    @Bean
    public RouterFunction<ServerResponse> apiRouterFunction() {
        return route()
                .POST("/{directory}/upload/files", this::handleFileUpload)
                .GET("/{directory}/retrieve/files/{filename}", this::handleFetchFile)
                .DELETE("/{directory}/remove/files/{filename}", this::handleDeleteFile)
                .DELETE("/remove/folders/{directory}", this::handleDeleteFolder)
                .build();
    }

    @NonNull
    public Mono<ServerResponse> handleFileUpload(final ServerRequest serverRequest) {
        var directory = serverRequest.pathVariable("directory");
        var generateFileName = Boolean.parseBoolean(serverRequest.queryParam("generate-file-name").orElse("false"));

        log.info("directory: {}", directory);

        Mono<List<String>> fileResponseMono = serverRequest.multipartData()
                .flatMap(parts -> {
                    log.info("parts.size(): {}", parts.size());
                    List<Part> fileParts = parts.get("files");

                    log.info("fileParts.size: {}", fileParts.size());

                    List<FilePart> filePartList = fileParts.stream()
                            .map(p -> (FilePart) p)
                            .toList();

                    return Flux.fromIterable(filePartList)
                            .flatMap(filePart -> DataBufferUtils.join(filePart.content())
                                    .map(DataBuffer::asInputStream)
                                    .map(inputStream -> storageService
                                            .uploadFile(inputStream, filePart.filename(), directory, generateFileName))
                                    .filter(StringUtils::isNotBlank))
                            .collectList();
                });

        return ServerResponse.ok().body(fileResponseMono, new ParameterizedTypeReference<>() {});
    }

    @NonNull
    public Mono<ServerResponse> handleFetchFile(final ServerRequest serverRequest) {
        var location = serverRequest.pathVariable("directory");
        var filename = serverRequest.pathVariable("filename");

        S3Blob blob = storageService.getFile(filename, location);
        if (blob != null) {
            return ServerResponse.ok()
                    .contentType(MediaType.parseMediaType(blob.contentType()))
                    .bodyValue(blob.content());
        }

        return ServerResponse.notFound().build();
    }

    @NonNull
    public Mono<ServerResponse> handleDeleteFile(final ServerRequest serverRequest) {
        var location = serverRequest.pathVariable("directory");
        var filename = serverRequest.pathVariable("filename");

        return ServerResponse.ok()
                .bodyValue(storageService.deleteFile(filename, location));
    }

    @NonNull
    public Mono<ServerResponse> handleDeleteFolder(final ServerRequest serverRequest) {
        var location = serverRequest.pathVariable("directory");

        return ServerResponse.ok()
                .bodyValue(storageService.deleteFolder(location));
    }
}
