package com.phivealpha.marketdata.apicoid;

import com.phivealpha.marketdata.apicoid.ApiCoIdData.Quality;

final class ApiCoIdFailure extends RuntimeException {
    final Quality quality;
    final String reason;

    ApiCoIdFailure(Quality quality, String reason) {
        super(reason, null, false, false);
        this.quality = quality;
        this.reason = reason;
    }
}
