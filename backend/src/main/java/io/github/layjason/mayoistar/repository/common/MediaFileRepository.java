package io.github.layjason.mayoistar.repository.common;

import io.github.layjason.mayoistar.entity.common.MediaFile;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MediaFileRepository extends JpaRepository<MediaFile, String> {

    List<MediaFile> findByMediaIdIn(Collection<String> mediaIds);
}
