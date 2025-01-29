package net.engineeringdigest.journalApp.service;

import lombok.extern.slf4j.Slf4j;
import net.engineeringdigest.journalApp.entity.User;
import net.engineeringdigest.journalApp.repository.UserRepository;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@Component
@Slf4j
public class UserService {
    @Autowired
    private UserRepository userRepository;

    private static final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();


    public boolean saveNewUser(User user){
        try{
            user.setRoles(Arrays.asList("USER"));
            user.setPassword(passwordEncoder.encode(user.getPassword()));
            userRepository.save(user);
            return true;
        }
        catch (Exception e){
            log.error("Error occurred for the userName {}",user.getUserName());
            return false;
        }
    };

    public void saveAdmin(User user){
        user.setRoles(Arrays.asList("USER","ADMIN"));
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        userRepository.save(user);
    };

    public void saveUser(User user){
        userRepository.save(user);
    };

    public List<User> getAll(){

        return userRepository.findAll();
    }

    public Optional<User> findById(ObjectId id){

        return userRepository.findById(id);
    }

    public void deleteById(ObjectId id){
        userRepository.deleteById(id);
    }

    public User finduserByUserName(String user){
        return userRepository.findByUserName(user);

    }
}

// controller --> service --> repository