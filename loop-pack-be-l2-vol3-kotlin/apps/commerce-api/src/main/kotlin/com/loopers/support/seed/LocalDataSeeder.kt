package com.loopers.support.seed

import com.loopers.domain.brand.BrandDescription
import com.loopers.domain.brand.BrandModel
import com.loopers.domain.brand.BrandName
import com.loopers.domain.brand.BrandRepository
import com.loopers.domain.product.LikeCount
import com.loopers.domain.product.Price
import com.loopers.domain.product.ProductModel
import com.loopers.domain.product.ProductName
import com.loopers.domain.product.ProductRepository
import com.loopers.domain.user.BirthDate
import com.loopers.domain.user.Email
import com.loopers.domain.user.LoginId
import com.loopers.domain.user.RawPassword
import com.loopers.domain.user.UserCommand
import com.loopers.domain.user.UserName
import com.loopers.domain.user.UserService
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * 로컬 확인용 시드 데이터.
 *
 * 브랜드/상품 등록 API 가 없어 시더가 없으면 .http 로 확인할 수 있는 것이 빈 목록과 404 뿐이다.
 * local 프로필은 ddl-auto: create 라 재기동할 때마다 테이블이 비므로 중복 삽입 걱정이 없다.
 * (ddl-auto 를 update 로 바꾸면 기동할 때마다 데이터가 쌓인다. 그때는 중복 방지 장치가 필요하다.)
 *
 * data.sql 대신 코드로 넣는 이유는 BaseEntity 의 createdAt / updatedAt 이 @PrePersist 로 채워지기 때문이다.
 * SQL 직접 INSERT 는 이 not null 컬럼을 손으로 채워야 하고 값 객체 검증도 우회한다.
 *
 * 상품의 likeCount 는 좋아요 행 없이 만들어진 합성 값이다.
 * 정합을 맞추려면 회원 50명과 좋아요 수천 건이 필요한데, likes_desc 정렬 확인이라는 원래 목적에 비해 얻는 것이 없다.
 * 좋아요 API 는 상대 증감만 하므로 출발값이 무엇이든 정확하게 동작한다. (설계 문서 9.2 장)
 */
@Profile("local")
@Component
class LocalDataSeeder(
    private val brandRepository: BrandRepository,
    private val productRepository: ProductRepository,
    private val userService: UserService,
) : ApplicationRunner {
    private val log = LoggerFactory.getLogger(LocalDataSeeder::class.java)

    @Transactional
    override fun run(args: ApplicationArguments) {
        // 회원이 없으면 like-v1.http 를 실행할 때마다 회원가입부터 해야 한다.
        // loginId 를 loopers01 로 두지 않는 이유는 user-v1.http 의 첫 요청이 그 ID 로 가입하기 때문이다.
        // 시더가 선점하면 그 파일이 409 로 깨진다.
        val users = USER_SEEDS.map { loginId ->
            userService.signUp(
                UserCommand.SignUp(
                    loginId = LoginId(loginId),
                    password = RawPassword(SEED_PASSWORD),
                    name = UserName("시드회원"),
                    birthDate = BirthDate.from(SEED_BIRTH_DATE),
                    email = Email("$loginId@loopers.com"),
                ),
            )
        }

        val brands = BRAND_SEEDS.map { (name, description) ->
            brandRepository.save(BrandModel.create(BrandName(name), BrandDescription(description)))
        }

        val products = (0 until PRODUCT_COUNT).map { index ->
            val brand = brands[index % brands.size]
            ProductModel.create(
                brandId = brand.id,
                name = ProductName("${brand.name.value} 상품 ${index + 1}"),
                // 가격을 20종으로 좁혀 같은 가격의 상품이 여러 개 생기게 한다.
                // price_asc 정렬에서 id 보조 정렬이 동작하는지를 .http 로 눈으로 볼 수 있다.
                price = Price(((index % 20) + 1) * 1_000L),
                // 난수가 아니라 인덱스 기반 결정적 값이라, 다시 돌려도 같은 정렬 결과가 나온다.
                likeCount = LikeCount(((index * 7) % 50).toLong()),
            )
        }
        productRepository.saveAll(products)

        log.info("로컬 시드 데이터 생성 완료 : 회원 {}명, 브랜드 {}개, 상품 {}개", users.size, brands.size, products.size)
    }

    companion object {
        /** seeduser01 은 정확히 10자로, LoginId 의 상한이다. */
        private val USER_SEEDS = listOf("seeduser01", "seeduser02", "seeduser03")

        /** 영문·숫자·특수문자를 모두 포함하는 8자이며, 생년월일 19900101 을 포함하지 않는다. */
        private const val SEED_PASSWORD = "Seeder1!"
        private const val SEED_BIRTH_DATE = "1990-01-01"

        /** 기본 페이지 크기 20 기준 7페이지가 되어 페이징 경계를 확인할 수 있는 부피다. */
        private const val PRODUCT_COUNT = 137

        /** 설명이 빈 브랜드를 하나 섞어 BrandDescription.EMPTY 응답도 확인할 수 있게 한다. */
        private val BRAND_SEEDS = listOf(
            "루퍼스" to "일상을 조금 낫게",
            "몬드리안" to "선과 색으로 짓는 물건",
            "하바나" to "",
            "코드그린" to "재생 소재만 씁니다",
            "여백" to "덜어낼수록 좋아지는 것들",
        )
    }
}
