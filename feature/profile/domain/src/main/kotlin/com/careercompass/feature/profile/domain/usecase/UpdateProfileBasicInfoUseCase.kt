package com.careercompass.feature.profile.domain.usecase

import com.careercompass.core.domain.repository.UserProfileRepository
import com.careercompass.core.model.user.UserProfile
import com.careercompass.core.model.user.UserProfileUpdate
import javax.inject.Inject

/**
 * 기본 정보 수정 — `PATCH /users/me` (API_SPEC v0.1 §2).
 *
 * **바뀐 필드만 담아 보낸다.** 부분 수정이라 안 보낸 필드는 서버가 건드리지 않고, 바뀐 것이 하나도 없으면
 * 리포지토리가 요청 없이 현재 프로필을 돌려준다([UserProfileUpdate.isEmpty]) — 저장을 눌렀는데 아무것도
 * 안 바꾼 경우까지 왕복을 만들지 않는다.
 *
 * 검증은 화면이 이미 [com.careercompass.core.model.user.ProfileBasicInfoRules] 로 끝냈다. 여기서 한 번 더
 * 보지 않는 이유는 규칙이 두 자리에 있으면 어긋나기 때문이고, 그럼에도 서버가 거부하면(400 `INVALID_INPUT`)
 * 그 사유는 필드까지 담겨 실패로 올라온다([com.careercompass.core.domain.error.CoreDataFailure.InvalidInput]).
 */
public class UpdateProfileBasicInfoUseCase
    @Inject
    constructor(
        private val userProfileRepository: UserProfileRepository,
    ) {
        public suspend operator fun invoke(update: UserProfileUpdate): Result<UserProfile> = userProfileRepository.updateProfile(update)
    }
