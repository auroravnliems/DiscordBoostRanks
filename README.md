# DiscordBoostRank

Plugin addon cho DiscordSRV để tự động trao rank cho người chơi khi họ boost Discord server.

## Yêu cầu

- **Minecraft Server**: Spigot/Paper 1.16+
- **Java**: 17+
- **Plugins bắt buộc**:
  - DiscordSRV (1.25.0+)
  - LuckPerms (5.4+)

## Cài đặt

1. Build plugin từ source code:
   ```bash
   ./gradlew build
   ```

2. File JAR sẽ được tạo ở: `build/libs/DiscordBoostRank-1.0.0.jar`

3. Copy file JAR vào thư mục `plugins/` của server

4. Khởi động lại server

5. Chỉnh sửa config tại `plugins/DiscordBoostRank/config.yml`

## Cấu hình

```yaml
# Rank/Group sẽ được trao khi boost server
boost-rank: "Booster"

# Có tự động xóa rank khi hết boost không?
remove-on-unboost: true

# Messages
messages:
  rank-granted: "&aYou have received &e{rank} &arank for boosting our Discord server!"
  rank-removed: "&cYour &e{rank} &crank has been removed as you are no longer boosting the server."
  not-linked: "&cYou need to link your Discord account first! Use /discord link"
  
# Debug mode
debug: false

# Kiểm tra boost status mỗi X phút
periodic-check-minutes: 30
```

## Tạo Boost Rank trong LuckPerms

Trước khi sử dụng, bạn cần tạo group "Booster" trong LuckPerms:

```
/lp creategroup Booster
/lp group Booster meta setprefix "&d[Booster] "
/lp group Booster permission set some.permission.here
```

## Lệnh

- `/boostrankadmin` hoặc `/bra` - Hiển thị help
- `/bra reload` - Reload config
- `/bra check <player>` - Kiểm tra boost status của người chơi

**Permission**: `discordboostrank.admin`

## Cách hoạt động

1. Người chơi link tài khoản Discord với Minecraft qua DiscordSRV (`/discord link`)
2. Khi người chơi boost Discord server, plugin tự động:
   - Phát hiện boost event
   - Kiểm tra xem Discord user có link với Minecraft account không
   - Tự động add rank "Booster" (hoặc tên bạn đặt) vào LuckPerms
   - Gửi thông báo cho người chơi (nếu đang online)
3. Khi hết boost, plugin tự động xóa rank (nếu `remove-on-unboost: true`)

## Periodic Check

Plugin có chức năng kiểm tra định kỳ để đảm bảo không bỏ sót:
- Mặc định: Mỗi 30 phút kiểm tra tất cả boosters
- Nếu phát hiện ai đó đang boost nhưng chưa có rank → tự động cấp
- Nếu phát hiện ai đó không boost nhưng vẫn có rank → tự động xóa

## Debug Mode

Bật debug mode trong config để xem log chi tiết:
```yaml
debug: true
```

Logs sẽ hiển thị:
- Khi có người boost/unboost
- Khi cấp/xóa rank
- Khi chạy periodic check

## Support

Nếu có vấn đề:
1. Kiểm tra console logs
2. Bật debug mode
3. Dùng lệnh `/bra check <player>` để kiểm tra status

## License

MIT License - Free to use and modify
