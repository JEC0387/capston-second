"""
DB 테이블 정의 (SQLAlchemy ORM) — 서버 시작 시 Supabase 에 자동 생성된다.

테이블 8개
  users              사용자 (폰마다 1명 — 앱이 보내는 기기 번호로 구분, 번호 없으면 데모 사용자)
  clothes            옷장 옷 (앱 WardrobeStore 와 같은 내용, client_id = 앱의 옷 id)
  wear_logs          착용 기록 (앱 WearLogStore — 날짜별로 입은 옷 id 목록)
  body_measurements  체형 분석 결과 기록 (비율 + cm 추정값, 사진은 저장하지 않음)
  style_ratings      취향 학습 좋아요/싫어요 기록
  products           쇼핑 상품
  product_likes      상품 하트
  size_charts        브랜드별 사이즈표 (사이즈 추천용, CSV 로 채움)

동기화 원칙: 앱(폰)에 저장된 내용이 기준이고, 서버 DB 는 그 내용을 그대로 따라 저장한다.
"""
from datetime import datetime
from typing import Optional

from sqlalchemy import (
    JSON, Boolean, DateTime, Float, ForeignKey, Integer, String, Text,
    UniqueConstraint, func,
)
from sqlalchemy.orm import Mapped, mapped_column, relationship

from database import Base


class User(Base):
    __tablename__ = "users"

    id: Mapped[int] = mapped_column(primary_key=True)
    email: Mapped[Optional[str]] = mapped_column(String(255), unique=True, index=True)
    device_id: Mapped[Optional[str]] = mapped_column(String(64), unique=True, index=True)  # 앱 기기 번호 (로그인 전 사용자 구분)
    password_hash: Mapped[Optional[str]] = mapped_column(String(255))  # 로그인 기능 붙일 때 사용
    nickname: Mapped[str] = mapped_column(String(50))
    height_cm: Mapped[Optional[float]] = mapped_column(Float)
    body_type: Mapped[Optional[str]] = mapped_column(String(20))
    preferred_styles: Mapped[list] = mapped_column(JSON, default=list)
    sample_records_added: Mapped[Optional[bool]] = mapped_column(Boolean, default=False)  # 시연용 측정 기록을 넣었는지
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), server_default=func.now())

    clothes: Mapped[list["Clothing"]] = relationship(back_populates="user", cascade="all, delete-orphan")


class Clothing(Base):
    __tablename__ = "clothes"
    __table_args__ = (UniqueConstraint("user_id", "client_id", name="uq_clothes_user_client"),)

    id: Mapped[int] = mapped_column(primary_key=True)
    user_id: Mapped[int] = mapped_column(ForeignKey("users.id", ondelete="CASCADE"), index=True)
    client_id: Mapped[str] = mapped_column(String(64))        # 앱 WardrobeItem.id (예: sample_01, UUID)
    name: Mapped[str] = mapped_column(String(100))
    category: Mapped[str] = mapped_column(String(20))         # 상의/하의/아우터/신발/가방/모자
    sub_category: Mapped[Optional[str]] = mapped_column(String(30))  # 반팔 상의, 긴바지 …
    detail_type: Mapped[Optional[str]] = mapped_column(String(30))   # 니트, 청바지 …
    season: Mapped[Optional[str]] = mapped_column(String(20))
    style: Mapped[Optional[str]] = mapped_column(String(20))
    color: Mapped[str] = mapped_column(String(9), default="#EDE3D5")  # #RRGGBB
    color_name: Mapped[Optional[str]] = mapped_column(String(20))
    image_url: Mapped[str] = mapped_column(Text, default="")  # 대표 사진 주소 (지금은 폰 안 주소)
    original_uri: Mapped[Optional[str]] = mapped_column(Text)
    cutout_uri: Mapped[Optional[str]] = mapped_column(Text)
    garment_uri: Mapped[Optional[str]] = mapped_column(Text)
    is_cutout: Mapped[bool] = mapped_column(Boolean, default=False)
    ai_tagged: Mapped[bool] = mapped_column(Boolean, default=False)
    added_at: Mapped[Optional[datetime]] = mapped_column(DateTime(timezone=True))
    favorited_at: Mapped[Optional[datetime]] = mapped_column(DateTime(timezone=True))  # 하트 누른 시각
    kept_at: Mapped[Optional[datetime]] = mapped_column(DateTime(timezone=True))       # "계속 둘게요" 시각
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), server_default=func.now())
    updated_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), server_default=func.now(), onupdate=func.now()
    )

    user: Mapped[User] = relationship(back_populates="clothes")


class WearLog(Base):
    __tablename__ = "wear_logs"
    __table_args__ = (UniqueConstraint("user_id", "date", name="uq_wear_logs_user_date"),)

    id: Mapped[int] = mapped_column(primary_key=True)
    user_id: Mapped[int] = mapped_column(ForeignKey("users.id", ondelete="CASCADE"), index=True)
    date: Mapped[str] = mapped_column(String(10))             # yyyy-MM-dd
    item_ids: Mapped[list] = mapped_column(JSON, default=list)  # clothes.client_id 목록
    memo: Mapped[str] = mapped_column(Text, default="")
    updated_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), server_default=func.now(), onupdate=func.now()
    )


class BodyMeasurement(Base):
    __tablename__ = "body_measurements"
    __table_args__ = (UniqueConstraint("user_id", "client_id", name="uq_body_user_client"),)
    client_id: Mapped[Optional[str]] = mapped_column(String(64), nullable=True)

    id: Mapped[int] = mapped_column(primary_key=True)
    user_id: Mapped[int] = mapped_column(ForeignKey("users.id", ondelete="CASCADE"), index=True)
    body_type: Mapped[str] = mapped_column(String(20))        # 역삼각형/삼각형/모래시계형/직사각형
    confidence: Mapped[int] = mapped_column(Integer, default=0)  # 0~100
    # 가장 넓은 부위 대비 비율 (0~1)
    shoulder_ratio: Mapped[Optional[float]] = mapped_column(Float)
    waist_ratio: Mapped[Optional[float]] = mapped_column(Float)
    hip_ratio: Mapped[Optional[float]] = mapped_column(Float)
    leg_ratio: Mapped[Optional[float]] = mapped_column(Float)  # 다리 길이 / 키
    shoulder_hip_ratio: Mapped[Optional[float]] = mapped_column(Float)
    waist_hip_ratio: Mapped[Optional[float]] = mapped_column(Float)
    # cm 추정값 (키를 기준으로 환산한 정면 너비 — 둘레가 아님). 측정 실패 시 비어 있음
    height_cm: Mapped[Optional[float]] = mapped_column(Float)
    shoulder_cm: Mapped[Optional[float]] = mapped_column(Float)
    waist_cm: Mapped[Optional[float]] = mapped_column(Float)
    hip_cm: Mapped[Optional[float]] = mapped_column(Float)
    leg_cm: Mapped[Optional[float]] = mapped_column(Float)
    torso_cm: Mapped[Optional[float]] = mapped_column(Float)
    measured_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), server_default=func.now())
    is_sample: Mapped[Optional[bool]] = mapped_column(Boolean, default=False)  # 시연용 샘플 기록 (실제 측정 아님)


class StyleRating(Base):
    __tablename__ = "style_ratings"

    id: Mapped[int] = mapped_column(primary_key=True)
    user_id: Mapped[int] = mapped_column(ForeignKey("users.id", ondelete="CASCADE"), index=True)
    style: Mapped[str] = mapped_column(String(20))
    liked: Mapped[bool] = mapped_column(Boolean)
    rated_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), server_default=func.now())


class Product(Base):
    __tablename__ = "products"

    id: Mapped[int] = mapped_column(primary_key=True)
    mall_name: Mapped[Optional[str]] = mapped_column(String(30))   # 무신사/지그재그/4910
    brand: Mapped[str] = mapped_column(String(50))
    name: Mapped[str] = mapped_column(String(200))
    price: Mapped[int] = mapped_column(Integer)
    original_price: Mapped[Optional[int]] = mapped_column(Integer)
    rating: Mapped[float] = mapped_column(Float, default=0)
    review_count: Mapped[int] = mapped_column(Integer, default=0)
    bg_color: Mapped[str] = mapped_column(String(9), default="#EDE3D5")
    badge: Mapped[Optional[str]] = mapped_column(String(20))
    category: Mapped[Optional[str]] = mapped_column(String(20))
    image_url: Mapped[str] = mapped_column(Text, default="")
    product_url: Mapped[str] = mapped_column(Text, default="")     # 구매 페이지
    description: Mapped[str] = mapped_column(Text, default="")
    colors: Mapped[list] = mapped_column(JSON, default=list)
    sizes: Mapped[list] = mapped_column(JSON, default=list)


class ProductLike(Base):
    __tablename__ = "product_likes"

    user_id: Mapped[int] = mapped_column(ForeignKey("users.id", ondelete="CASCADE"), primary_key=True)
    product_id: Mapped[int] = mapped_column(ForeignKey("products.id", ondelete="CASCADE"), primary_key=True)
    liked_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), server_default=func.now())


class SizeChart(Base):
    __tablename__ = "size_charts"

    id: Mapped[int] = mapped_column(primary_key=True)
    brand: Mapped[str] = mapped_column(String(50), index=True)
    category: Mapped[str] = mapped_column(String(20), index=True)
    size_label: Mapped[str] = mapped_column(String(10))       # S / M / L / 95 / 100 …
    shoulder_cm: Mapped[Optional[float]] = mapped_column(Float)
    chest_cm: Mapped[Optional[float]] = mapped_column(Float)
    waist_cm: Mapped[Optional[float]] = mapped_column(Float)
    hip_cm: Mapped[Optional[float]] = mapped_column(Float)
    length_cm: Mapped[Optional[float]] = mapped_column(Float)
    measure_type: Mapped[str] = mapped_column(String(10), default="단면")  # 단면 / 둘레
    source_url: Mapped[str] = mapped_column(Text, default="")
