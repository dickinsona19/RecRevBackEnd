package com.BossLiftingClub.BossLifting.Club;


import java.util.List;

public interface ClubService {
    ClubDTO createClub(ClubDTO clubDTO);
//    ClubDTO getClubById(Integer id);
//    List<ClubDTO> getAllClubs();
//    ClubDTO updateClub(long id, ClubDTO clubDTO);
    void deleteClub(long id);
}