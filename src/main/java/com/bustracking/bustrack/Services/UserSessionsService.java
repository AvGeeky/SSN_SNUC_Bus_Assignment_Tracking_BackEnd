package com.bustracking.bustrack.Services;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.bustracking.bustrack.mappings.UserSessionsMapping;
import com.bustracking.bustrack.entities.User_sessions;
@Service
public class UserSessionsService {
  private final  UserSessionsMapping mapper;
  public UserSessionsService(UserSessionsMapping mapper){
      this.mapper=mapper;
  }
  public User_sessions getsessionbyusername(String username){
      return mapper.getByusername(username);
  }
  public List<User_sessions> getAll(){
      return mapper.getAll();
  }
  @Transactional
   public Boolean create_session(User_sessions session){
        int rows_affected=mapper.insertUser_sessions(session);
        return rows_affected>0;
  }
  @Transactional
    public Boolean update_session(UUID id,String username,String password,String login_type){
      int rows_affected=mapper.updateUser_sesions(id,username,password,login_type);
      return rows_affected>0;
  }
  @Transactional
    public Boolean delete_session(UUID id){
      int rows_affected=mapper.deleteUser_sessions(id);
      return rows_affected>0;
  }
}
